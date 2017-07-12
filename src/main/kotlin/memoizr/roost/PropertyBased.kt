package memoizr.roost

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

fun forAll(numberOfRuns: Int = 100, block: PropertyBased.() -> Unit) {
    Seed.seed
    val currentMethod = Thread.currentThread().stackTrace[2]
    val map = mutableMapOf<KType, Int>()

    numberOfRuns.times { PropertyBased("${currentMethod.methodName}::$it", map).block() }
}

internal val Int.times: IntRange get() {
    return 1..this
}

internal fun Int.times(block: (Int) -> Unit) = (1..this).forEach(block)

class PropertyBased(val token: String, val mutableMap: MutableMap<KType, Int>) {

    fun KType.store() = this.apply { mutableMap[this]?.let { mutableMap[this] = it.inc() } ?: { mutableMap[this] = 0 }() }
    inline fun <reified T : Any> a(): T {
        val type = T::class().type.store()
        val tkn = "$token::${mutableMap[type]}"
        return instantiateClass(type, tkn) as T
    }

    inline fun <reified T : Any> a(t: TypedKType<*>): T {
        val type = T::class(t).type.store()
        val tkn = "$token::${mutableMap[type]}"
        return instantiateClass(type, tkn) as T
    }

    inline fun <reified T : Any> a(t: TypedKType<*>, t2: TypedKType<*>): T {
        val type = T::class(t, t2).type.store()
        val tkn = "$token::${mutableMap[type]}"
        return instantiateClass(type, tkn) as T
    }

    inline fun <reified T : Any> a(t: TypedKType<*>, t2: TypedKType<*>, t3: TypedKType<*>): T {
        val type = T::class(t, t2, t3).type.store()
        val tkn = "$token::${mutableMap[type]}"
        return instantiateClass(type, tkn) as T
    }

    inline fun <reified T : Any> a(t: TypedKType<*>, t2: TypedKType<*>, t3: TypedKType<*>, t4: TypedKType<*>): T {
        val type = T::class(t, t2, t3, t4).type.store()
        val tkn = "$token::${mutableMap[type]}"
        return instantiateClass(type, tkn) as T
    }

    inline fun <reified T : Any> a(t: TypedKType<*>, t2: TypedKType<*>, t3: TypedKType<*>, t4: TypedKType<*>, t5: TypedKType<*>): T {
        val type = T::class(t, t2, t3, t4, t5).type.store()
        val tkn = "$token::${mutableMap[type]}"
        return instantiateClass(type, tkn) as T
    }

}

data class TypedKType<T : Any>(val t: KClass<T>, val type: KType)

inline operator fun <reified T : Any> KClass<out T>.invoke(vararg types: TypedKType<*>): TypedKType<T> =
        TypedKType(T::class, this.createType(
                types.zip(this.typeParameters.map { it.variance })
                        .map { KTypeProjection(it.second, it.first.type) }
        ))
