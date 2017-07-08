package memoizr.roost

import org.apache.commons.lang3.RandomStringUtils
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import java.lang.reflect.Array.newInstance
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

class aRandomListOf<out T : Any>(private val size: Int? = null) {
    operator fun getValue(host: Any, property: KProperty<*>): List<T> {
        val typeOfListItems = property.returnType.arguments.first().type!!.outProjection()
        val hostClassName = host::class.java.canonicalName
        val propertyName = property.name
        val list = aList(typeOfListItems, "$hostClassName::$propertyName", emptySet(), size?.dec())
        return list as List<T>
    }
}

fun KType.outProjection() = KTypeProjection(OUT, this)

class aRandom<out T : Any>(private val customization: T.() -> T = { this }) {
    private var t: T? = null
    private var lastSeed = Seed.seed

    operator fun getValue(hostClass: Any, property: KProperty<*>): T {
        fun getClass() = instantiateClazz<T>(property.returnType, "${hostClass::class.java.canonicalName}::${property.name}")
                .let { customization(it) }

        return if (t != null && lastSeed == Seed.seed) t!! else getClass().apply {
            lastSeed = Seed.seed
            t = this
        }
    }
}

internal fun hashString(string: String): Long = UUID.nameUUIDFromBytes(string.toByteArray()).mostSignificantBits
internal fun aChar(token: String = ""): Char = pseudoRandom(token).nextInt(59319).toChar()
internal fun anInt(token: String = "", max: Int? = null): Int = max?.let { pseudoRandom(token).nextInt(it) } ?: pseudoRandom(token).nextInt()
internal fun aLong(token: String = ""): Long = pseudoRandom(token).nextLong()
internal fun aDouble(token: String = ""): Double = pseudoRandom(token).nextDouble()
internal fun aShort(token: String = ""): Short = pseudoRandom(token).nextInt(Short.MAX_VALUE.toInt()).toShort()
internal fun aFloat(token: String = ""): Float = pseudoRandom(token).nextFloat()
internal fun aByte(token: String = ""): Byte = pseudoRandom(token).nextInt(255).toByte()
internal fun aBoolean(token: String = ""): Boolean = pseudoRandom(token).nextBoolean()
internal fun aString(token: String = ""): String = pseudoRandom(token).let {
    RandomStringUtils.random(Math.max(1, it.nextInt(Seed.maxStringLength)), 0, 59319, true, true, null, it)
}


internal fun aList(typeProjection: KTypeProjection, token: String, parentClasses: Set<KClass<*>>, size: Int? = null): List<*> {
    val type = typeProjection.type!!
    val klass = type.jvmErasure

    parentClasses shouldNotContain klass

    val items = (0..(size ?: pseudoRandom(token).nextInt(5)))

    return items.map {
        if (klass == List::class) {
            aList(type.arguments.first(), "$token::$it", parentClasses)
        } else instantiateClazz<Any>(type, "$token::$it", parentClasses)
    }
}

internal fun <R : Any?> instantiateClazz(type: KType, token: String = "", parentClasses: Set<KClass<*>> = emptySet()): R {
    val klass = getArrayClass(type)

    parentClasses shouldNotContain klass

    val result = if (shouldBeNullable(type, token)) null else when {
        klass in objectFactory -> objectFactory[klass]?.invoke(type, parentClasses, token)
        klass.java.isEnum -> klass.java.enumConstants[anInt(max = klass.java.enumConstants.size)]
        klass.objectInstance != null -> klass.objectInstance
        klass.java.isArray -> instantiateArray(type, token, parentClasses, klass)
        klass.java.isInterface || klass.isSealed -> instantiateInterface<R>(klass, token, parentClasses)
        else -> instantiateArbitraryClass<R>(klass, token, type, parentClasses)
    }
    return result as R
}

private fun instantiateArray(type: KType, token: String, past: Set<KClass<*>>, klass: KClass<out Any>): Array<Any?> {
    val typeProjection = type.arguments.first()
    val list = aList(typeProjection, token, past.plus(klass))
    val array = newInstance(typeProjection.type!!.jvmErasure!!.java, list.size) as Array<Any?>
    return array.apply { list.forEachIndexed { index, any -> array[index] = any } }
}

operator fun <T> Boolean.rangeTo(yes: () -> T): T? = if (this) yes() else null
operator fun <T> Boolean.invoke(yes: () -> T): T? = if (this) yes() else null

private fun <R : Any?> instantiateInterface(klass: KClass<out Any>, token: String, past: Set<KClass<*>>): R {
    val allClassesInModule = classes.isEmpty().then {
        Reflections("", SubTypesScanner(false)).getSubTypesOf(Any::class.java).apply { classes.addAll(this) }
    } ?: classes
    val implementations = classesMap[klass] ?: allClassesInModule
            .filter { klass.java != it && klass.java.isAssignableFrom(it) }
            .apply { classesMap.put(klass, this) }

    return if (implementations.isEmpty())
        instantiateNewInterface(klass, token, past) as R
    else {
        val implementation = implementations[pseudoRandom(token).nextInt(implementations.size)]
        instantiateClazz<R>(implementation.kotlin.createType(), "$token::${implementation.name}")
    }
}

private fun shouldBeNullable(type: KType, token: String) = type.isMarkedNullable && pseudoRandom(token).nextInt() % 2 == 0

infix private fun Set<KClass<*>>.shouldNotContain(klass: KClass<*>) {
    if (isAllowedCyclic(klass) && this.contains(klass)) throw CyclicException()
}

private fun isAllowedCyclic(klass: KClass<out Any>) = klass != List::class && klass != Set::class && klass != Map::class && !klass.java.isArray

private fun <T : Any> instantiateNewInterface(klass: KClass<T>, token: String, past: Set<KClass<*>>): T {
    val members = klass.members.plus(java.lang.Object::class.members)
    val print: Array<Method> = klass.java.methods + Any::class.java.methods
    val methMap = print.map { method ->
        method to members.filter { member ->
            val x: Boolean = (method.name == member.name || method.name == "get${member.name.capitalize()}")
            x && method.parameters.map { it.parameterizedType } ==
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

internal fun <R : Any?> instantiateArbitraryClass(klass: KClass<out Any>, token: String, type: KType, past: Set<KClass<*>>): R {
    val constructors = klass.constructors.filter { !it.parameters.any { (it.type.jvmErasure == klass) } }.toList()
    if (constructors.size == 0 && klass.constructors.any { it.parameters.any { (it.type.jvmErasure == klass) } }) throw CyclicException()
    val defaultConstructor = constructors[pseudoRandom(token).nextInt(constructors.size)] as KFunction<R>
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
        val namedParameters = parameters.zip(defaultConstructor.parameters.map { it.name }).map { "${it.second}=${it.first}" }
        throw CreationException("""Something went wrong when trying to instantiate class ${klass}
         using constructor: $defaultConstructor
         with values: ${namedParameters}""", e.cause)
    }
}

val Boolean.not: Boolean get() {
    return !this
}

val Boolean.then: Positive get() {
    return Positive(this)
}

val Boolean.otherwise: Negative get() {
    return Negative(this)
}

class Positive(private val boolean: Boolean) {
    operator fun <T> invoke(yes: () -> T): T? = if (boolean) yes() else null
}

class Negative(private val boolean: Boolean) {
    operator fun <T> invoke(yes: () -> T): T? = if (boolean) null else yes()
}

private fun getArrayClass(type: KType): KClass<out Any> {
    return type.jvmErasure.java.isArray.otherwise { type.jvmErasure } ?: when (type) {
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

private val classes: MutableSet<Class<out Any>> = mutableSetOf()
private val classesMap: MutableMap<KClass<out Any>, List<Class<out Any>>> = mutableMapOf()

fun pseudoRandom(token: String): Random = Random(Seed.seed + hashString(token))

val objectFactory = mutableMapOf<KClass<out Any>, (KType, Set<KClass<*>>, String) -> Any>()

