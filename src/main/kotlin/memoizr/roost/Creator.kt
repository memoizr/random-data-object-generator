package memoizr.roost

import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class Creator(private var token: String) {
    object some

    fun <T> any(): T = some as T

    inline fun <reified A, reified R : Any> ((A) -> R).create(a: A = any()): R {
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

    inline fun <A, B, reified R : Any> ((A, B) -> R).create(a: A = any(), b: B = any()): R {
        val token = ""
        val klass = R::class
        val type = R::class.createType()
        val constructors = klass.constructors.filter { !it.parameters.any { (it.type.jvmErasure == klass) } }.toList()
        if (constructors.size == 0 && klass.constructors.any { it.parameters.any { (it.type.jvmErasure == klass) } }) throw CyclicException()
        val defaultConstructor: KFunction<R> = constructors[pseudoRandom(token).nextInt(constructors.size)]
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
        val res = if (param.type.isMarkedNullable && pseudoRandom(token).nextBoolean()) null else {
            instantiateClazz(tpe, "$token::${tpe.javaType.typeName}::$param")
        } as A
        return res
    }

    val aChar by lazy { aChar(token) }
    val anInt by lazy { anInt(token) }
    val aLong by lazy { aLong(token) }
    val aDouble by lazy { aDouble(token) }
    val aShort by lazy { aShort(token) }
    val aFloat by lazy { aFloat(token) }
    val aByte by lazy { aByte(token) }
    val aBoolean by lazy { aBoolean(token) }
    fun aString(): String {
        val res = aString(token)
        token = "$token::$"
        return res
    }

    fun <T> choose(vararg any: T): T = any[Random(aLong).nextInt(any.size)]
}