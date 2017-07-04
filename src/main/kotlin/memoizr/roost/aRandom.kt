package memoizr.roost

import org.apache.commons.lang3.RandomStringUtils
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import java.io.File
import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.KVariance.INVARIANT
import kotlin.reflect.KVariance.OUT
import kotlin.reflect.full.createType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import kotlin.system.measureTimeMillis

class aRandomListOf<out T : Any>(private val size: Int? = null) {
    operator fun getValue(hostClass: Any, property: KProperty<*>): List<T> {
        val type = property.returnType.arguments.first().type
        return aList(KTypeProjection(OUT, type), hostClass::class.java.canonicalName + "::" + property.name, emptySet(), size?.dec()) as List<T>
    }
}

class aRandom<T : Any>(private val custom: T.() -> T = { this }) {
    var t: T? = null
    var lastSeed = Seed.seed

    operator fun getValue(hostClass: Any, property: KProperty<*>): T {
        return if (t != null && lastSeed == Seed.seed) t!! else instantiateClazz<T>(property.returnType, hostClass::class.java.canonicalName + "::" + property.name).let { custom(it) }
                .apply {
                    lastSeed = Seed.seed
                    t = this
                }
    }
}

class CyclicException : Throwable("Illegal cyclic dependency")

object some

fun <T> some(): T = some as T

object Seed {
    internal var testing = false
    var seed = Random().nextLong()
        set(value) {
            field = value
            if (!testing) println("overriding seed: $value")
        }
    val maxStringLength = 20

    init {
        println("setting seed: $seed")
        fun list(type: KType, token: String, past: Set<KClass<*>>) = aList(type.arguments.first(), token, past.plus(type.jvmErasure))
        fun <T : Any> list(klass: KClass<T>, token: String, past: Set<KClass<*>>): List<T> = aList(KTypeProjection(OUT, klass.createType()), token, past) as List<T>

        objectFactory.put(kotlin.String::class, { type, past, token -> aString(token) })
        objectFactory.put(kotlin.Byte::class, { type, past, token -> aByte(token) })
        objectFactory.put(kotlin.Int::class, { type, past, token -> anInt(token) })
        objectFactory.put(kotlin.Long::class, { type, past, token -> aLong(token) })
        objectFactory.put(kotlin.Double::class, { type, past, token -> aDouble(token) })
        objectFactory.put(kotlin.Short::class, { type, past, token -> aShort(token) })
        objectFactory.put(kotlin.Float::class, { type, past, token -> aFloat(token) })
        objectFactory.put(kotlin.Boolean::class, { type, past, token -> aBoolean(token) })
        objectFactory.put(kotlin.Char::class, { type, past, token -> aChar(token) })

        objectFactory.put(File::class, { type, past, token -> File(aString(token)) })

        objectFactory.put(List::class, { type, past, token -> list(type, token, past) })
        objectFactory.put(Set::class, { type, past, token -> list(type, token, past).toSet() })
        objectFactory.put(kotlin.collections.Map::class, { type, past, token ->
            list(type, token, past).map { Pair(it, instantiateClazz<Any>(type.arguments[1].type!!, token)) }.toMap()
        })

        objectFactory.put(IntArray::class, { type, past, token -> list(Int::class, token, past).toIntArray() })
        objectFactory.put(Array<Int>::class, { type, past, token -> list(Int::class, token, past).toTypedArray() })
        objectFactory.put(ShortArray::class, { type, past, token -> list(Short::class, token, past).toShortArray() })
        objectFactory.put(Array<Short>::class, { type, past, token -> list(Short::class, token, past).toTypedArray() })
        objectFactory.put(LongArray::class, { type, past, token -> list(Long::class, token, past).toLongArray() })
        objectFactory.put(Array<Long>::class, { type, past, token -> list(Long::class, token, past).toTypedArray() })
        objectFactory.put(FloatArray::class, { type, past, token -> list(Float::class, token, past).toFloatArray() })
        objectFactory.put(Array<Float>::class, { type, past, token -> list(Float::class, token, past).toTypedArray() })
        objectFactory.put(DoubleArray::class, { type, past, token -> list(Double::class, token, past).toDoubleArray() })
        objectFactory.put(Array<Double>::class, { type, past, token -> list(Double::class, token, past).toTypedArray() })
        objectFactory.put(BooleanArray::class, { type, past, token -> list(Boolean::class, token, past).toBooleanArray() })
        objectFactory.put(Array<Boolean>::class, { type, past, token -> list(Boolean::class, token, past).toTypedArray() })
        objectFactory.put(ByteArray::class, { type, past, token -> list(Byte::class, token, past).toByteArray() })
        objectFactory.put(Array<Byte>::class, { type, past, token -> list(Byte::class, token, past).toTypedArray() })
        objectFactory.put(CharArray::class, { type, past, token -> list(Char::class, token, past).toCharArray() })
        objectFactory.put(Array<Char>::class, { type, past, token -> list(Char::class, token, past).toTypedArray() })
    }
}

inline fun <reified T : Any> custom(noinline t: () -> T) {
    objectFactory.put(T::class, { type, past, token -> t() })
}

inline fun <reified A, reified R : Any> ((A) -> R).create(a: A = some()): R {
    val token = ""
    val klass = R::class
    val type = R::class.createType()
    val constructors = klass.constructors.filter { !it.parameters.any { (it.type.jvmErasure == klass) } }.toList()
    if (constructors.size == 0 && klass.constructors.any { it.parameters.any { (it.type.jvmErasure == klass) } }) throw CyclicException()
    val defaultConstructor: KFunction<R> = constructors.filter { it.parameters[0].type.jvmErasure == A::class }.first()
    defaultConstructor.isAccessible = true
    val constructorParameters: List<KParameter> = defaultConstructor.parameters
    val params = type.arguments.toMutableList()
    val param = constructorParameters[0]
    val res = doit<A>(param, params, token)
    return invoke(if (a == some) res else a)
}

inline fun <A, B, reified R : Any> ((A, B) -> R).create(a: A = some(), b: B = some()): R {
    val token = ""
    val klass = R::class
    val type = R::class.createType()
    val constructors = klass.constructors.filter { !it.parameters.any { (it.type.jvmErasure == klass) } }.toList()
    if (constructors.size == 0 && klass.constructors.any { it.parameters.any { (it.type.jvmErasure == klass) } }) throw CyclicException()
    val defaultConstructor: KFunction<R> = constructors[Random(getSeed(token)).nextInt(constructors.size)]
    defaultConstructor.isAccessible = true
    val constructorParameters: List<KParameter> = defaultConstructor.parameters
    val params = type.arguments.toMutableList()
    val param = constructorParameters[0]
    val res = doit<A>(param, params, token)
    val resb = doit<B>(constructorParameters[1], params, token)
    return invoke(if (a == some) res else a, if (b == some) resb else b)
}

fun <A> doit(param: KParameter, params: MutableList<KTypeProjection>, token: String): A {
    val tpe = if (param.type.jvmErasure == Any::class) params.removeAt(0).type!! else param.type
    val res = if (param.type.isMarkedNullable && Random(getSeed(token)).nextBoolean()) null else {
        instantiateClazz<Any>(tpe, "$token::${tpe.javaType.typeName}::$param")
    } as A
    return res
}

private fun hashString(string: String): Long = UUID.nameUUIDFromBytes(string.toByteArray()).mostSignificantBits

private fun aString(token: String = ""): String = Random(getSeed(token)).let {
    RandomStringUtils.random(Math.max(1, it.nextInt(Seed.maxStringLength)), 0, 59319, true, true, null, it)
}

private fun aChar(token: String = ""): Char = Random(getSeed(token)).nextInt(59319).toChar()
private fun anInt(token: String = ""): Int = Random(getSeed(token)).nextInt()
private fun aLong(token: String = ""): Long = Random(getSeed(token)).nextLong()
private fun aDouble(token: String = ""): Double = Random(getSeed(token)).nextDouble()
private fun aShort(token: String = ""): Short = Random(getSeed(token)).nextInt(Short.MAX_VALUE.toInt()).toShort()
private fun aFloat(token: String = ""): Float = Random(getSeed(token)).nextFloat()
private fun aByte(token: String = ""): Byte = Random(getSeed(token)).nextInt(255).toByte()
private fun aBoolean(token: String = ""): Boolean = Random(getSeed(token)).nextBoolean()

private fun aList(typeProjection: KTypeProjection, token: String, past: Set<KClass<*>>, size: Int? = null): List<*> {
    val klass = typeProjection.type!!.jvmErasure
    if (isAllowedCyclic(klass) && past.contains(klass)) throw CyclicException()
    val range = size ?: Random(getSeed(token)).nextInt(5)
    return if (klass == List::class) {
        (0..range).map {
            aList(typeProjection.type!!.arguments.first(), "$token::$it", past)
        }
    } else {
        (0..range).map { instantiateClazz<Any>(typeProjection.type!!, "$token::$it") }
    }
}

internal fun <T : Any> T.print() = this.apply {
    val stackFrame = Thread.currentThread().stackTrace[2]
    val className = stackFrame.className
    val methodName = stackFrame.methodName
    val fileName = stackFrame.fileName
    val lineNumber = stackFrame.lineNumber
    println("$this at $className.$methodName($fileName:$lineNumber)")
}

private fun <R : Any?> instantiateClazz(type: KType, token: String = "", past: Set<KClass<*>> = emptySet()): R {
    val klass = getArrayClass(type)
    if (isAllowedCyclic(klass) && past.contains(klass)) throw CyclicException()
    val result = if (type.isMarkedNullable && Random(getSeed(token)).nextInt() % 2 == 0) null else when {
        klass in objectFactory -> {
            objectFactory[klass]?.invoke(type, past, token)
        }
        klass.java.isEnum -> klass.java.enumConstants[Random(getSeed(token)).nextInt(klass.java.enumConstants.size)]
        klass.objectInstance != null -> klass.objectInstance
        klass.java.isArray -> instantiateArray(type, token, past, klass)
        klass.java.isInterface || klass.isSealed -> instantiateInterface<R>(klass, token, past)
        else -> instantiateArbitraryClass<R>(klass, token, type, past)
    }
    return result as R
}

private fun isAllowedCyclic(klass: KClass<out Any>) = klass != List::class && klass != Set::class && klass != Map::class && !klass.java.isArray

private fun instantiateArray(type: KType, token: String, past: Set<KClass<*>>, klass: KClass<out Any>): Array<Any?> {
    val list = aList(type.arguments.first(), token, past.plus(klass))
    val array = java.lang.reflect.Array.newInstance(type.arguments.first()!!.type!!.jvmErasure!!.java, list.size) as Array<Any?>
    list.forEachIndexed { index, any -> array[index] = any }
    return array
}

private fun <R : Any?> instantiateInterface(klass: KClass<out Any>, token: String, past: Set<KClass<*>>): R {
    val allClasses: MutableSet<out Class<out Any>> =
            if (classes.isEmpty())
                Reflections("", SubTypesScanner(false)).getSubTypesOf(Any::class.java).apply { classes.addAll(this) }
            else classes
    val implementations = classesMap[klass] ?: allClasses
            .filter { klass.java.isAssignableFrom(it) && klass.java != it }
            .apply { classesMap.put(klass, this) }

    if (implementations.size == 0)
        return instantiateNewInterface(klass, token, past) as R
    else {
        val implementation = implementations[Random(getSeed(token)).nextInt(implementations.size)]
        return instantiateClazz<R>(implementation.kotlin.createType(), "$token::${implementation.name}")
    }
}

private fun <T : Any> instantiateNewInterface(klass: KClass<T>, token: String, past: Set<KClass<*>>): T {
    val members = klass.members.plus(java.lang.Object::class.members)
    val print: Array<Method> = klass.java.methods + Any::class.java.methods
    val methMap = print.map { method ->
        method to members.filter { member ->
            val x: Boolean = (method.name == member.name || method.name == "get${member.name.capitalize()}")
            x && method.parameters.map { it.parameterizedType} ==
                    member.valueParameters.map { it.type.javaType }
        }.firstOrNull()
    }.toMap()
    val res = java.lang.reflect.Proxy.newProxyInstance(
            klass.java.classLoader,
            arrayOf(klass.java),
            { proxy, method, obj ->
                when (method.name) {
                    "hashCode" -> proxy.toString().hashCode()
                    "equals" -> proxy.toString().equals(obj[0].toString())
                    "toString" -> "\$RandomImplementation$${klass.simpleName}"
                    else -> methMap[method]?.let { instantiateClazz<T>(it.returnType, token, past) } ?:
                            instantiateClazz(method.returnType.kotlin.createType(), token, past)
                }
            }
    )
    return res as T
}

private fun <R : Any?> instantiateArbitraryClass(klass: KClass<out Any>, token: String, type: KType, past: Set<KClass<*>>): R {
    val constructors = klass.constructors.filter { !it.parameters.any { (it.type.jvmErasure == klass) } }.toList()
    if (constructors.size == 0 && klass.constructors.any { it.parameters.any { (it.type.jvmErasure == klass) } }) throw CyclicException()
    val defaultConstructor = constructors[Random(getSeed(token)).nextInt(constructors.size)] as KFunction<R>
    defaultConstructor.isAccessible = true
    val constructorParameters: List<KParameter> = defaultConstructor.parameters
    val params = type.arguments.toMutableList()
    val parameters = (constructorParameters.map {
            val tpe = if (it.type.jvmErasure == Any::class) params.removeAt(0).type!! else it.type
            instantiateClazz<Any>(tpe, "$token::${tpe.jvmErasure}::$it", past.plus(klass))
        }).toTypedArray()
    try {
        val res = defaultConstructor.call(*parameters)
        return res
    } catch (e: Throwable) {
        throw CreationException("""Something went wrong when trying to instantiate class ${klass}
         using constructor: $defaultConstructor
         with values: ${parameters.toList()}""", e.cause)
    }
}

class CreationException(message : String, cause: Throwable?): Exception(message, cause)

private fun getArrayClass(type: KType): KClass<out Any> {
    return if (!type.jvmErasure.java.isArray) type.jvmErasure else when (type) {
        IntArray::class.createType() -> IntArray::class
        Array<Int>::class.createType(listOf(KTypeProjection(INVARIANT, Int::class.createType()))) -> Array<Int>::class
        ShortArray::class.createType() -> ShortArray::class
        Array<Short>::class.createType(listOf(KTypeProjection(INVARIANT, Short::class.createType()))) -> Array<Short>::class
        LongArray::class.createType() -> LongArray::class
        Array<Long>::class.createType(listOf(KTypeProjection(INVARIANT, Long::class.createType()))) -> Array<Long>::class
        FloatArray::class.createType() -> FloatArray::class
        Array<Float>::class.createType(listOf(KTypeProjection(INVARIANT, Float::class.createType()))) -> Array<Float>::class
        DoubleArray::class.createType() -> DoubleArray::class
        Array<Double>::class.createType(listOf(KTypeProjection(INVARIANT, Double::class.createType()))) -> Array<Double>::class
        BooleanArray::class.createType() -> BooleanArray::class
        Array<Boolean>::class.createType(listOf(KTypeProjection(INVARIANT, Boolean::class.createType()))) -> Array<Boolean>::class
        ByteArray::class.createType() -> ByteArray::class
        Array<Byte>::class.createType(listOf(KTypeProjection(INVARIANT, Byte::class.createType()))) -> Array<Byte>::class
        CharArray::class.createType() -> CharArray::class
        Array<Char>::class.createType(listOf(KTypeProjection(INVARIANT, Char::class.createType()))) -> Array<Char>::class
        else -> type.jvmErasure
    }
}

internal fun <T> printTime(message: String = "", block: () -> T): T {
    var result: T? = null
    val time = measureTimeMillis {
        result = block()
    }
    println("$message $time")
    return result!!
}

private val classes: MutableSet<Class<out Any>> = mutableSetOf()
private val classesMap: MutableMap<KClass<out Any>, List<Class<out Any>>> = mutableMapOf()

fun getSeed(token: String): Long = Seed.seed + hashString(token)

val objectFactory = mutableMapOf<KClass<out Any>, (KType, Set<KClass<*>>, String) -> Any>()

