package memoizr.roost

import com.sun.deploy.util.ReflectionUtil.isSubclassOf
import com.sun.jndi.toolkit.url.Uri
import jdk.nashorn.internal.objects.NativeArray.some
import org.apache.commons.lang3.RandomStringUtils
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import java.io.File
import java.net.URI
import java.security.SecureRandom.getSeed
import java.util.*
import javax.xml.bind.DatatypeConverter.printTime
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

class aRandomListOf<out T : Any>(private val size: Int? = null) {
    operator fun getValue(hostClass: Any, property: KProperty<*>): List<T> {
        val type = property.returnType.arguments.first().type
        return aList(KTypeProjection(KVariance.OUT, type), hostClass::class.java.canonicalName + "::" + property.name, emptySet(), size?.dec())
    }
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

public fun <R : Any?> aList(typeProjection: KTypeProjection, token: String, past: Set<KClass<*>>, size: Int? = null): R {
    val klass = typeProjection.type!!.jvmErasure
    if ((klass != List::class && klass != Set::class && klass != Map::class) && past.contains(klass)) throw CyclicException()
    val range = size ?: Random(getSeed(token)).nextInt(5)
    return if (klass == List::class) {
        (0..range).map {
            aList<R>(typeProjection.type!!.arguments.first(), "$token::$it", past)
        } as R
    } else {
        (0..range).map { instantiateClazz<R>(typeProjection.type!!, "$token::$it") } as R
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
//inline fun anArray(clazz: KClass<*>, type: KTypeProjection, token: String, past: Set<KClass<*>>): Array<*> {
//    Arras
//    return
//}

public fun <R : Any?> instantiateClazz(type: KType, token: String = "", past: Set<KClass<*>> = emptySet()): R {
    val klass = type.jvmErasure
    if ((klass != List::class && klass != Set::class && klass != Map::class) && past.contains(klass)) throw CyclicException()
    return if (type.isMarkedNullable && Random(getSeed(token)).nextInt() % 2 == 0) null as R else when {
        objectFactory.contains(klass.java) -> {
           objectFactory.get(klass.java)?.invoke(token) as R
        }
        klass.java.canonicalName == String::class.java.canonicalName -> aString(token) as R
        klass.java.isEnum -> klass.java.enumConstants[Random(getSeed(token)).nextInt(klass.java.enumConstants.size)] as R
        klass == IntArray::class -> (aList<Any>(KTypeProjection(KVariance.OUT, Int::class.createType()), token, past) as List<Int>).toTypedArray() as R
        Array<Any>::class.java.isAssignableFrom(klass.java) -> {
             val list = (aList<R>(type.arguments.first(), token, past.plus(klass)) as List<Any?>)
            val array = java.lang.reflect.Array.newInstance(type.arguments.first()!!.type!!.jvmErasure!!.java, list.size) as Array<Any?>
            list.forEachIndexed { index, any ->
                array[index] = any
            }
            array as R
        }
        klass == kotlin.collections.List::class -> aList<R>(type.arguments.first(), token, past.plus(klass)) as R
        klass == kotlin.collections.Set::class -> (aList<R>(type.arguments.first(), token, past.plus(klass)) as List<*>).toSet() as R
        klass == kotlin.collections.Map::class -> {
            val keys = aList<Any>(type.arguments.first(), token, past.plus(klass)) as List<*>
            keys.map { Pair(it, instantiateClazz<Any>(type.arguments[1].type!!, token)) }.toMap() as R
        }
        klass == File::class -> File(aString(token)) as R
        klass == kotlin.String::class -> aString(token) as R
        klass == kotlin.Byte::class -> aByte(token) as R
        klass == kotlin.Int::class -> anInt(token) as R
        klass == kotlin.Long::class -> aLong(token) as R
        klass == kotlin.Double::class -> aDouble(token) as R
        klass == kotlin.Short::class -> aShort(token) as R
        klass == kotlin.Float::class -> aFloat(token) as R
        klass == kotlin.Boolean::class -> aBoolean(token) as R
        klass == kotlin.Char::class -> aChar(token) as R
        klass.objectInstance != null -> klass.objectInstance as R
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
            val constructors = klass.print().constructors.filter { !it.parameters.any { (it.type.jvmErasure == klass) } }.toList()
            if (constructors.size == 0 && klass.constructors.any { it.parameters.any { (it.type.jvmErasure == klass) } }) throw CyclicException()
            val defaultConstructor: KFunction<R> = constructors[Random(getSeed(token)).nextInt(constructors.size)] as KFunction<R>
            defaultConstructor.isAccessible = true
            val constructorParameters: List<KParameter> = defaultConstructor.parameters
            val params = type.arguments.toMutableList()
            defaultConstructor.call(*(constructorParameters.map {
                val tpe = if (it.type.jvmErasure == Any::class) params.removeAt(0).type!! else it.type
                if (it.type.isMarkedNullable && Random(getSeed(token)).nextBoolean()) null else {
                    instantiateClazz<Any>(tpe, "$token::${tpe.javaType.typeName}::$it", past.plus(klass))
                }
            }).toTypedArray().apply { this::class.print() })
        }
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

public fun getSeed(token: String): Long = Seed.seed + hashString(token)

class aRandom<out T : Any>(private val custom: T.() -> T = { this }) {
    operator fun getValue(hostClass: Any, property: KProperty<*>): T {
        return (instantiateClazz<T>(property.returnType, hostClass::class.java.canonicalName + "::" + property.name) as T).let { custom(it) }
    }
}

val objectFactory = mutableMapOf<Class<out Any>, (String) -> Any>()

object Seed {
    var seed = Random().nextLong()
        set(value) {
            field = value
//            println("overriding seed: $value")
        }
    val maxStringLength = 20

    init {
        println("setting seed: $seed")
    }
}

class CyclicException : Throwable("Illegal cyclic dependency")

inline fun <reified T : Any> custom(noinline t: () -> T) {
    objectFactory.put(T::class.java, { s -> t() })
}

fun <T> some(): T = some as T

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
    val defaultConstructor: KFunction<R> = constructors[Random(getSeed(token)).nextInt(constructors.size)] as KFunction<R>
    defaultConstructor.isAccessible = true
    val constructorParameters: List<KParameter> = defaultConstructor.parameters
    val params = type.arguments.toMutableList()
    val param = constructorParameters[0]
    val res = doit<A>(param, params, token)
    val resb = doit<B>(constructorParameters[1], params, token)
    return invoke(if (a == some) res else a, if (b == some) resb else b)
}

public fun <A> doit(param: KParameter, params: MutableList<KTypeProjection>, token: String): A {
    val tpe = if (param.type.jvmErasure == Any::class) params.removeAt(0).type!! else param.type
    val res = if (param.type.isMarkedNullable && Random(getSeed(token)).nextBoolean()) null else {
        instantiateClazz<Any>(tpe, "$token::${tpe.javaType.typeName}::$param")
    } as A
    return res
}


object some

