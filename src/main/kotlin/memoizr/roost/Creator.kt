package memoizr.roost

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class Creator(var token: String) {
    object some

    fun <T> any(): T = some as T

    operator inline fun <reified A, reified R : Any> ((A) -> R).get(a: A = any()): R {
        val params = getParameters(R::class, A::class)
        return invoke(a or new<A>(params[0], token))
    }

    operator inline fun <reified A, reified B, reified R : Any> ((A, B) -> R).get(a: A = any(), b: B = any()): R {
        val constructorParameters = getParameters(R::class, A::class, B::class)
        return invoke(
                a.or(new<A>(constructorParameters[0], token)),
                b.or(new<B>(constructorParameters[1], token))
        )
    }

    operator inline fun <reified A, reified B, reified C, reified R : Any> ((A, B, C) -> R).get(a: A = any(), b: B = any(), c: C = any()): R {
        val constructorParameters = getParameters(R::class, A::class, B::class, C::class)
        return invoke(
                a.or(new<A>(constructorParameters[0], token)),
                b.or(new<B>(constructorParameters[1], token)),
                c.or(new<C>(constructorParameters[2], token))
        )
    }

    operator inline fun <reified A, reified B, reified C, reified D, reified R : Any> ((A, B, C, D) -> R).get(a: A = any(), b: B = any(), c: C = any(), d: D = any()): R {
        val constructorParameters = getParameters(R::class, A::class, B::class, C::class, D::class)
        return invoke(
                a.or(new<A>(constructorParameters[0], token)),
                b.or(new<B>(constructorParameters[1], token)),
                c.or(new<C>(constructorParameters[2], token)),
                d.or(new<D>(constructorParameters[3], token))
        )
    }

    operator inline fun <reified A, reified B, reified C, reified D, reified E, reified R : Any>
            ((A, B, C, D, E) -> R).get(a: A = any(), b: B = any(), c: C = any(), d: D = any(), e: E = any()): R {
        val params = getParameters(R::class, A::class, B::class, C::class, D::class, E::class)
        return invoke(
                a.or(new<A>(params[0], token)),
                b.or(new<B>(params[1], token)),
                c.or(new<C>(params[2], token)),
                d.or(new<D>(params[3], token)),
                e.or(new<E>(params[4], token))
        )
    }

    fun <R : Any> getConstructor(klass: KClass<R>) = klass.constructors.filter { !it.parameters.any { (it.type.jvmErasure == klass) } }.toList()

    infix fun <T> T.or(other: T) = if (this == some) other else this

    fun <R : Any> getParameters(klass: KClass<R>, vararg classes: KClass<*>): List<KParameter> {
        val constructors = getConstructor(klass)
        if (constructors.isEmpty() && klass.constructors.any { it.parameters.any { (it.type.jvmErasure == klass) } }) throw CyclicException()
        val defaultConstructor: KFunction<R> = constructors.filter {
            it.parameters.size == classes.size &&
                    it.parameters
                            .zip(classes)
                            .all {
                                it.first.type.jvmErasure == it.second ||
                                it.first.type.jvmErasure.java.isAssignableFrom(it.second.java) }
        }.first()
        defaultConstructor.isAccessible = true
        val constructorParameters: List<KParameter> = defaultConstructor.parameters
        return constructorParameters
    }

    fun <A> new(parameter: KParameter, token: String): A {
        val tpe = parameter.type
        return instantiateClass(tpe, "$token::${tpe.javaType.typeName}::$parameter") as A
    }

//    val aChar by lazy { aChar(token) }
//    val anInt by lazy { anInt(token) }
//    val aLong by lazy { aLong(token) }
//    val aDouble by lazy { aDouble(token) }
//    val aShort by lazy { aShort(token) }
//    val aFloat by lazy { aFloat(token) }
//    val aByte by lazy { aByte(token) }
//    val aBoolean by lazy { aBoolean(token) }
//    fun aString(): String {
//        val res = aString(token)
//        token = "$token::$"
//        return res
//    }

//    fun <T> choose(vararg any: T): T = any[Random(aLong).nextInt(any.size)]
}