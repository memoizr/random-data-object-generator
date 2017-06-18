package memoizr.roost

import org.apache.commons.lang3.RandomStringUtils
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import java.security.SecureRandom.getSeed
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class aRandomListOf<out T>() {
    operator fun getValue(hostClass: Any, property: KProperty<*>): List<T> {
        return aList(KTypeProjection(KVariance.OUT, property.returnType.arguments.first().type), hostClass::class.java.canonicalName + "::" + property.name, emptySet())
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

private fun <R : Any> aList(typeParameter: KTypeProjection, token: String, past: Set<KClass<*>>): R {
    val klass = typeParameter.type!!.jvmErasure
    if (klass != List::class && past.contains(klass)) throw CyclicException()
    return (0..Random(getSeed(token)).nextInt(10)).map { instantiateClazz(klass, "$token::$it", listOf(typeParameter)) } as R
}

fun <R : Any> instantiateClazz(klass: KClass<R>, token: String = "", typeParameters: List<KTypeProjection> = emptyList(), past: Set<KClass<*>> = emptySet()): R {
    if (klass != List::class && past.contains(klass)) throw CyclicException()
    return when {
        Creator.objectFactory.contains(klass.java) -> {
            Creator.objectFactory.get(klass.java)?.invoke() as R
        }
        klass.java.canonicalName == String::class.java.canonicalName -> aString(token) as R
        klass.java.isEnum -> klass.java.enumConstants[Random(getSeed(token)).nextInt(klass.java.enumConstants.size)]
        klass == kotlin.collections.List::class -> aList(typeParameters.first(), token, past.plus(klass))
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
            println(implementation)
            instantiateClazz(implementation.kotlin, "$token::${implementation.name}")
        }
        else -> {
            val constructors = klass.constructors.toList()
            println(klass)
            val defaultConstructor: KFunction<R> = constructors[Random(getSeed(token)).nextInt(constructors.size)]
            defaultConstructor.isAccessible = true
            val constructorParameters: List<KParameter> = defaultConstructor.parameters
            defaultConstructor.call(*(constructorParameters.map {
                val typeArguments: List<KTypeProjection> = it.type.arguments
                if (it.type.isMarkedNullable && Random(getSeed(token)).nextBoolean()) null else {
                    instantiateClazz(it.type.jvmErasure, "$token::${it.type.javaType.typeName}", typeArguments, past.plus(klass))
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

class aRandom<out T>(private val custom: T.() -> T = { this }) {
    operator fun getValue(hostClass: Any, property: KProperty<*>): T {
        return (instantiateClazz(property.returnType.jvmErasure, hostClass::class.java.canonicalName + "::" + property.name) as T).let { custom(it) }
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
