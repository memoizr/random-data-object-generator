package memoizr.roost

import org.apache.commons.lang3.RandomStringUtils
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import java.lang.reflect.Array.newInstance
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.KVariance.INVARIANT
import kotlin.reflect.full.createType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class aRandomListOf<out T : Any>(private val size: Int? = null) {
    operator fun getValue(host: Any, property: KProperty<*>): List<T> {
        val typeOfListItems = property.returnType.arguments.first().type!!
        val hostClassName = host::class.java.canonicalName
        val propertyName = property.name
        val list = aList(typeOfListItems, "$hostClassName::$propertyName", emptySet(), size?.dec())
        return list as List<T>
    }
}

class aRandom<out T : Any>(private val customization: T.() -> T = { this }) {
    private var t: T? = null
    private var lastSeed = Seed.seed

    operator fun getValue(hostClass: Any, property: KProperty<*>): T {

        return if (t != null && lastSeed == Seed.seed) t!!
        else instantiateClass(property.returnType, "${hostClass::class.java.canonicalName}::${property.name}").let {
            lastSeed = Seed.seed
            val res = it as T
            t = res
            customization(res)
        }
    }
}

private val maxChar = 59319

internal fun hashString(string: String): Long = UUID.nameUUIDFromBytes(string.toByteArray()).mostSignificantBits
internal fun aChar(token: String): Char = pseudoRandom(token).nextInt(maxChar).toChar()
internal fun anInt(token: String, max: Int? = null): Int = max?.let { pseudoRandom(token).nextInt(it) } ?: pseudoRandom(token).nextInt()
internal fun aLong(token: String): Long = pseudoRandom(token).nextLong()
internal fun aDouble(token: String): Double = pseudoRandom(token).nextDouble()
internal fun aShort(token: String): Short = pseudoRandom(token).nextInt(Short.MAX_VALUE.toInt()).toShort()
internal fun aFloat(token: String): Float = pseudoRandom(token).nextFloat()
internal fun aByte(token: String): Byte = pseudoRandom(token).nextInt(255).toByte()
internal fun aBoolean(token: String): Boolean = pseudoRandom(token).nextBoolean()
internal fun aString(token: String): String = pseudoRandom(token).let {
    RandomStringUtils.random(Math.max(1, it.nextInt(Seed.maxStringLength)), 0, maxChar, true, true, null, it)
}

internal fun aList(type: KType, token: String, parentClasses: Set<KClass<*>>, size: Int? = null): List<*> {
    val klass = type.jvmErasure

    parentClasses shouldNotContain klass

    val items = 0..(size ?: pseudoRandom(token).nextInt(5))

    return items.map {
        if (klass == List::class) {
            aList(type.arguments.first().type!!, "$token::$it", parentClasses)
        } else instantiateClass(type, "$token::$it", parentClasses)
    }
}

fun instantiateClass(type: KType, token: String = "", parentClasses: Set<KClass<*>> = emptySet()): Any? {
    fun KClass<out Any>.isAnInterfaceOrSealed() = this.java.isInterface || this.isSealed
    fun KClass<out Any>.isAnArray() = this.java.isArray
    fun KClass<out Any>.isAnEnum() = this.java.isEnum
    fun KClass<out Any>.isAnObject() = this.objectInstance != null
    fun thereIsACustomFactory() = type in objectRepo
    fun isNullable(): Boolean = type.isMarkedNullable && pseudoRandom(token).nextInt() % 2 == 0

    val klass = getArrayClass(type)
    parentClasses shouldNotContain klass

    return when {
        isNullable() -> null
        thereIsACustomFactory() -> objectRepo[type]?.invoke(type, parentClasses, token)
        klass.isAnObject() -> klass.objectInstance
        klass.isAnEnum() -> klass.java.enumConstants[anInt(token, max = klass.java.enumConstants.size)]
        klass.isAnArray() -> instantiateArray(type, token, parentClasses, klass)
        klass.isAnInterfaceOrSealed() -> instantiateInterface(klass, token, parentClasses)
        else -> instantiateArbitraryClass(klass, token, type, parentClasses)
    }
}

private fun instantiateArray(type: KType, token: String, past: Set<KClass<*>>, klass: KClass<out Any>): Array<Any?> {
    val genericType = type.arguments.first().type!!
    val list = aList(genericType, token, past.plus(klass))
    val array = newInstance(genericType.jvmErasure!!.java, list.size) as Array<Any?>
    return array.apply { list.forEachIndexed { index, any -> array[index] = any } }
}

private fun instantiateInterface(klass: KClass<out Any>, token: String, past: Set<KClass<*>>): Any {
    val allClassesInModule = classes.isEmpty().then {
        Reflections("", SubTypesScanner(false)).getSubTypesOf(Any::class.java).apply { classes.addAll(this) }
    } ?: classes
    val allImplementationsInModule = classesMap[klass] ?: allClassesInModule
            .filter { klass.java != it && klass.java.isAssignableFrom(it) }
            .apply { classesMap.put(klass, this) }

    return allImplementationsInModule.getOrNull(pseudoRandom(token).int(allImplementationsInModule.size))
            ?.let { instantiateClass(it.kotlin.createType(), "$token::${it.name}") }
            ?: instantiateNewInterface(klass, token, past)
}

private fun instantiateNewInterface(klass: KClass<*>, token: String, past: Set<KClass<*>>): Any {
    val kMembers = klass.members.plus(Object::class.members)
    val javaMethods: Array<Method> = klass.java.methods + Any::class.java.methods
    val methodReturnTypes = javaMethods.map { method ->
        val returnType = kMembers.find { member ->
            fun hasNameName(): Boolean = (method.name == member.name || method.name == "get${member.name.capitalize()}")
            fun hasSameArguments() = method.parameters.map { it.parameterizedType } == member.valueParameters.map { it.type.javaType }
            hasNameName() && hasSameArguments()
        }?.returnType
        method to returnType
    }.toMap()

    return Proxy.newProxyInstance(klass.java.classLoader, arrayOf(klass.java)) { proxy, method, obj ->
        when (method.name) {
            Any::hashCode.javaMethod?.name.toString() -> proxy.toString().hashCode()
            Any::equals.javaMethod?.name.toString() -> proxy.toString().equals(obj[0].toString())
            Any::toString.javaMethod?.name.toString() -> "\$RandomImplementation$${klass.simpleName}"
            else -> methodReturnTypes[method]?.let { instantiateClass(it, token, past) } ?:
                    instantiateClass(method.returnType.kotlin.createType(), token, past)
        }
    }
}

infix private fun Set<KClass<*>>.shouldNotContain(klass: KClass<*>) {
    if (isAllowedCyclic(klass) && this.contains(klass)) throw CyclicException()
}

internal fun instantiateArbitraryClass(klass: KClass<out Any>, token: String, type: KType, past: Set<KClass<*>>): Any? {
    val constructors = klass.constructors.filter { !it.parameters.any { (it.type.jvmErasure == klass) } }.toList()
    if (constructors.isEmpty() && klass.constructors.any { it.parameters.any { (it.type.jvmErasure == klass) } }) throw CyclicException()
    val defaultConstructor = constructors[pseudoRandom(token).int(constructors.size)] as KFunction<*>
    defaultConstructor.isAccessible = true
    val params = type.arguments.toMutableList()
    val parameters = (defaultConstructor.parameters.map {
        val tpe = if (it.type.jvmErasure == Any::class) params.removeAt(0).type!! else it.type
        instantiateClass(tpe, "$token::${tpe.jvmErasure}::$it", past.plus(klass))
    }).toTypedArray()
    try {
        val res = defaultConstructor.call(*parameters)
        return res
    } catch (e: Throwable) {
        val namedParameters = parameters.zip(defaultConstructor.parameters.map { it.name }).map { "${it.second}=${it.first}" }
        throw CreationException("""Something went wrong when trying to instantiate class ${klass}
         using constructor: $defaultConstructor
         with values: $namedParameters""", e.cause)
    }
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

private fun Random.int(bound: Int) = if (bound == 0) 0 else nextInt(bound)

private fun isAllowedCyclic(klass: KClass<out Any>) = klass != List::class && klass != Set::class && klass != Map::class && !klass.java.isArray

internal val Boolean.then: Positive get() {
    return Positive(this)
}

internal val Boolean.otherwise: Negative get() {
    return Negative(this)
}

internal class Positive(private val boolean: Boolean) {
    operator fun <T> invoke(yes: () -> T): T? = if (boolean) yes() else null
}

internal class Negative(private val boolean: Boolean) {
    operator fun <T> invoke(yes: () -> T): T? = if (boolean) null else yes()
}

private val classes: MutableSet<Class<out Any>> = mutableSetOf()
private val classesMap: MutableMap<KClass<out Any>, List<Class<out Any>>> = mutableMapOf()

fun pseudoRandom(token: String): Random = Random(Seed.seed + hashString(token))

//val objectFactory = mutableMapOf<KType, (KType, Set<KClass<*>>, String) -> Any>()

object objectRepo {
    private val objectFactory = mutableMapOf<KType, (KType, Set<KClass<*>>, String) -> Any>()

    operator fun set(type: KType, factory: (KType, Set<KClass<*>>, String) -> Any): objectRepo  {
        objectFactory[type] = factory
        return this
    }

    operator fun get(type: KType): ((KType, Set<KClass<*>>, String) -> Any)?  {
        return objectFactory[type] ?: objectFactory[type.jvmErasure.starProjectedType]
    }

    operator fun contains(type: KType): Boolean {
        return get(type)?.let { true } ?: false
    }
}

