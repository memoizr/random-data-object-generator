package memoizr.roost

import kotlin.reflect.full.starProjectedType

inline fun <reified T : Any> custom(noinline t: Creator.() -> T) {
    objectRepo[T::class.starProjectedType] = { type, past, token -> t(Creator(token)) }
}