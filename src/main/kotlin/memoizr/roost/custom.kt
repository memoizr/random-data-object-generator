package memoizr.roost

inline fun <reified T : Any> custom(noinline t: Creator.() -> T) {
    objectFactory.put(T::class, { type, past, token -> t(Creator(token)) })
}