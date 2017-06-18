package memoizr.roost

import org.apache.commons.lang3.RandomStringUtils
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import java.security.SecureRandom.getSeed
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class aRandomListOf<out T>(private val size: Int? = null) {
    operator fun getValue(hostClass: Any, property: KProperty<*>): List<T> {
        return aList(KTypeProjection(KVariance.OUT, property.returnType.arguments.first().type), hostClass::class.java.canonicalName + "::" + property.name, emptySet(), size?.dec())
    }
}

private fun hashString(string: String): Long = string.toByteArray().map(Byte::toLong).sum()

fun aString(token: String = ""): String {
    return Random(getSeed(token)).let {
        RandomStringUtils.random(Math.max(1, it.nextInt(Seed.maxStringLength)), 0, 59319, true, true, null, it)
    }
}

fun aChar(token: String = ""): Char = Random(getSeed(token)).nextInt(59319).toChar()
fun anInt(token: String = ""): Int = Random(getSeed(token)).nextInt()
fun aLong(token: String = ""): Long = Random(getSeed(token)).nextLong()
fun aDouble(token: String = ""): Double = Random(getSeed(token)).nextDouble()
fun aShort(token: String = ""): Short = Random(getSeed(token)).nextInt(Short.MAX_VALUE.toInt()).toShort()
fun aFloat(token: String = ""): Float = Random(getSeed(token)).nextFloat()
fun aByte(token: String = ""): Byte = Random(getSeed(token)).nextInt(255).toByte()

fun aBoolean(token: String = ""): Boolean {
    return Random(getSeed(token)).nextBoolean()
}

private fun <R : Any> aList(typeProjection: KTypeProjection, token: String, past: Set<KClass<*>>, size: Int? = null): R {
    val klass = typeProjection.type!!.jvmErasure
    if (klass != List::class && past.contains(klass)) throw CyclicException()
    val range = size ?: Random(getSeed(token)).nextInt(10)
    return if (klass == List::class) {
        (0..range).map { instantiateClazz<R>(typeProjection.type!!.arguments.first().type!!, "$token::$it", listOf(typeProjection.type!!.arguments.first())) } as R
    } else {
        (0..range).map { instantiateClazz<R>(typeProjection.type!!, "$token::$it", listOf(typeProjection)) } as R
    }
}

fun <R : Any> instantiateClazz(type: KType, token: String = "", typeProjections: List<KTypeProjection> = emptyList(), past: Set<KClass<*>> = emptySet()): R {
    val klass = type.jvmErasure
    if (klass != List::class && past.contains(klass)) throw CyclicException()
    return when {
        Creator.objectFactory.contains(klass.java) -> {
            Creator.objectFactory.get(klass.java)?.invoke() as R
        }
        klass.java.canonicalName == String::class.java.canonicalName -> aString(token) as R
        klass.java.isEnum -> klass.java.enumConstants[Random(getSeed(token)).nextInt(klass.java.enumConstants.size)] as R
        klass == kotlin.collections.List::class -> aList(typeProjections.first(), token, past.plus(klass))
        klass == kotlin.String::class -> aString(token) as R
        klass == kotlin.Byte::class -> aByte(token) as R
        klass == kotlin.Int::class -> anInt(token) as R
        klass == kotlin.Long::class -> aLong(token) as R
        klass == kotlin.Double::class -> aDouble(token) as R
        klass == kotlin.Short::class -> aShort(token) as R
        klass == kotlin.Short::class -> aShort(token) as R
        klass == kotlin.Float::class -> aFloat(token) as R
        klass == kotlin.Boolean::class -> aBoolean(token) as R
        klass == kotlin.Char::class -> aChar(token) as R
        klass.objectInstance != null -> klass.objectInstance as R
        klass.java.isInterface -> {
            val allClasses = Reflections("", SubTypesScanner(false)).getSubTypesOf(klass.java)
            val implementations = allClasses.filter { klass.java.isAssignableFrom(it) }
            val implementation = implementations[Random(getSeed(token)).nextInt(implementations.size)]
            val tpe = object : KType {
                override val arguments: List<KTypeProjection> = emptyList()
                override val classifier: KClassifier? = implementation.kotlin
                override val isMarkedNullable: Boolean = false

            }
            instantiateClazz<R>(tpe, "$token::${implementation.name}")
        }
        else -> {
            val constructors = klass.constructors.toList()
            val defaultConstructor: KFunction<R> = constructors[Random(getSeed(token)).nextInt(constructors.size)] as KFunction<R>
            defaultConstructor.isAccessible = true
            val constructorParameters: List<KParameter> = defaultConstructor.parameters
            defaultConstructor.call(*(constructorParameters.map {
                val typeArguments: List<KTypeProjection> = it.type.arguments
                instantiateClazz<Any>(it.type, "$token::${it.type.javaType.typeName}", typeArguments, past.plus(klass))
                if (it.type.isMarkedNullable && Random(getSeed(token)).nextBoolean()) null else {
                    instantiateClazz<Any>(it.type, "$token::${it.type.javaType.typeName}", typeArguments, past.plus(klass))
                }
            }).toTypedArray())
        }
    }
}

//inline fun <reified A : Any> forAll(block: (A) -> Unit) {
//    (1..10).forEach {
//        block(instantiateClazz(A::class, "$it", emptyList(), emptySet()))
//    }
//}
//
//inline fun <reified A : Any, reified B : Any> forAll(block: (A, B) -> Unit) {
//    (1..100).forEach {
//        val a = instantiateClazz(A::class, "${A::class}${it * 1345}", emptyList(), emptySet())
//        val b = instantiateClazz(B::class, "${B::class}${(0..it).map { it }}", emptyList(), emptySet())
//        block(a, b)
//    }
//}

private fun getSeed(token: String): Long = Seed.seed + hashString(token)

class aRandom<out T : Any>(private val custom: T.() -> T = { this }) {
    operator fun getValue(hostClass: Any, property: KProperty<*>): T {
        return (instantiateClazz<T>(property.returnType, hostClass::class.java.canonicalName + "::" + property.name) as T).let { custom(it) }
    }

}

object Creator {
    val objectFactory = mutableMapOf<Class<out Any>, () -> Any>()
}

object Seed {
    var seed = Random().nextLong()
        set(value) {
            field = value
            println("overriding seed: $value")
        }
    val maxStringLength = 20

    init {
        println("setting seed: $seed")
    }
}

class CyclicException : Throwable("Illegal cyclic dependency")
