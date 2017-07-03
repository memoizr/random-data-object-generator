package memoizr.roost

import org.apache.commons.lang3.RandomStringUtils
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import java.io.File
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.KVariance.INVARIANT
import kotlin.reflect.KVariance.OUT
import kotlin.reflect.full.createType
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

private fun aString(token: String = ""): String {
    return Random(getSeed(token)).let {
        RandomStringUtils.random(Math.max(1, it.nextInt(Seed.maxStringLength)), 0, 59319, true, true, null, it)
    }
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
    if ((klass != List::class && klass != Set::class && klass != Map::class) && past.contains(klass)) throw CyclicException()
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

    if ((klass != List::class && klass != Set::class && klass != Map::class && !klass.java.isArray) && past.contains(klass)) throw CyclicException()
    val result = if (type.isMarkedNullable && Random(getSeed(token)).nextInt() % 2 == 0) null else when {
        klass in objectFactory -> {
            objectFactory[klass]?.invoke(type, past, token)
        }
        klass.java.isEnum -> klass.java.enumConstants[Random(getSeed(token)).nextInt(klass.java.enumConstants.size)]
        klass.objectInstance != null -> klass.objectInstance
        klass.java.isArray -> {
            val list = aList(type.arguments.first(), token, past.plus(klass))
            val array = java.lang.reflect.Array.newInstance(type.arguments.first()!!.type!!.jvmErasure!!.java, list.size) as Array<Any?>
            list.forEachIndexed { index, any -> array[index] = any }
            array
        }
        klass.java.isInterface || klass.isSealed -> {
            val allClasses: MutableSet<out Class<out Any>> =
                    if (classes.isEmpty())
                        Reflections("", SubTypesScanner(false)).getSubTypesOf(Any::class.java).apply { classes.addAll(this) }
                    else classes
            val implementations = classesMap[klass] ?: allClasses
                    .filter { klass.java.isAssignableFrom(it) }
                    .apply { classesMap.put(klass, this) }

            val implementation = implementations[Random(getSeed(token)).nextInt(implementations.size)]
            instantiateClazz<R>(implementation.kotlin.createType(), "$token::${implementation.name}")
        }
        else -> {
            val constructors = klass.constructors.filter { !it.parameters.any { (it.type.jvmErasure == klass) } }.toList()
            if (constructors.size == 0 && klass.constructors.any { it.parameters.any { (it.type.jvmErasure == klass) } }) throw CyclicException()
            val defaultConstructor = constructors[Random(getSeed(token)).nextInt(constructors.size)] as KFunction<R>
            defaultConstructor.isAccessible = true
            val constructorParameters: List<KParameter> = defaultConstructor.parameters
            val params = type.arguments.toMutableList()
            defaultConstructor.call(*(constructorParameters.map {
                val tpe = if (it.type.jvmErasure == Any::class) params.removeAt(0).type!! else it.type
                instantiateClazz<Any>(tpe, "$token::${tpe.jvmErasure}::$it", past.plus(klass))
            }).toTypedArray())
        }
    }
    return result as R
}

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

