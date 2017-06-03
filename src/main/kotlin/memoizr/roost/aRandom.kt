package memoizr.roost

import org.apache.commons.lang3.RandomStringUtils
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class aRandom<out T>(private val custom: T.() -> T = { this }) {
    operator fun getValue(hostClass: Any, property: KProperty<*>): T {
        return (instantiateClazz(property.returnType.jvmErasure, hostClass::class.java.canonicalName + "::" + property.name) as T).let { custom(it) }
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
        if (past.contains(klass)) throw CyclicException()
        return (0..Random(getSeed(token)).nextInt(10)).map { instantiateClazz(klass, "$token::$it") } as R
    }

    private fun <R : Any> instantiateClazz(klass: KClass<R>, token: String = "", typeParameters: List<KTypeProjection> = emptyList(), past: Set<KClass<*>> = emptySet()): R {
        if (past.contains(klass)) throw CyclicException()
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
            else -> {
                val constructors = klass.constructors.toList()
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

    private fun getSeed(token: String) = Seed.seed + hashString(token)
}

object Creator {
    val objectFactory = mutableMapOf<Class<out Any>, () -> Any>()
}

object Seed {
    var seed: Long = Random().nextLong()
    val maxStringLength = 20
}

class CyclicException : Throwable("Illegal cyclic dependency")
