package memoizr.roost

import java.io.File
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.jvmErasure

object Seed {
    internal var testing = false
    var seed = Random().nextLong()
        set(value) {
            field = value
            if (!testing) println("overriding seed: $value")
        }
    val maxStringLength = 20

    init {
        println("setting seed: $seed")
        fun list(type: KType, token: String, past: Set<KClass<*>>) = aList(type.arguments.first().type!!, token, past.plus(type.jvmErasure))
        fun <T : Any> list(klass: KClass<T>, token: String, past: Set<KClass<*>>): List<T> = aList(klass.createType(), token, past) as List<T>

        objectRepo[kotlin.String::class.starProjectedType] = { type, past, token -> aString(token) }
        objectRepo[kotlin.Byte::class.starProjectedType] = { type, past, token -> aByte(token) }
        objectRepo[kotlin.Int::class.starProjectedType] = { type, past, token -> anInt(token) }
        objectRepo[kotlin.Long::class.starProjectedType] = { type, past, token -> aLong(token) }
        objectRepo[kotlin.Double::class.starProjectedType] = { type, past, token -> aDouble(token) }
        objectRepo[kotlin.Short::class.starProjectedType] = { type, past, token -> aShort(token) }
        objectRepo[kotlin.Float::class.starProjectedType] = { type, past, token -> aFloat(token) }
        objectRepo[kotlin.Boolean::class.starProjectedType] = { type, past, token -> aBoolean(token) }
        objectRepo[kotlin.Char::class.starProjectedType] = { type, past, token -> aChar(token) }

        objectRepo[File::class.starProjectedType] = { type, past, token -> File(aString(token)) }

        objectRepo[List::class.starProjectedType] = { type, past, token -> list(type, token, past) }
        objectRepo[Set::class.starProjectedType] = { type, past, token -> list(type, token, past).toSet() }
        objectRepo[kotlin.collections.Map::class.starProjectedType] = { type, past, token ->
            list(type, token, past).map { Pair(it, instantiateClass(type.arguments[1].type!!, token)) }.toMap()
        }

        objectRepo[IntArray::class(Int::class()).type] = { type, past, token -> list(Int::class, token, past).toIntArray() }
        objectRepo[Array<Int>::class(Int::class()).type] = { type, past, token -> list(Int::class, token, past).toTypedArray() }
        objectRepo[ShortArray::class(Short::class()).type] = { type, past, token -> list(Short::class, token, past).toShortArray() }
        objectRepo[Array<Short>::class(Short::class()).type] = { type, past, token -> list(Short::class, token, past).toTypedArray() }
        objectRepo[LongArray::class(Long::class()).type] = { type, past, token -> list(Long::class, token, past).toLongArray() }
        objectRepo[Array<Long>::class(Long::class()).type] = { type, past, token -> list(Long::class, token, past).toTypedArray() }
        objectRepo[FloatArray::class(Float::class()).type] = { type, past, token -> list(Float::class, token, past).toFloatArray() }
        objectRepo[Array<Float>::class(Float::class()).type] = { type, past, token -> list(Float::class, token, past).toTypedArray() }
        objectRepo[DoubleArray::class(Double::class()).type] = { type, past, token -> list(Double::class, token, past).toDoubleArray() }
        objectRepo[Array<Double>::class(Double::class()).type] = { type, past, token -> list(Double::class, token, past).toTypedArray() }
        objectRepo[BooleanArray::class(Boolean::class()).type] = { type, past, token -> list(Boolean::class, token, past).toBooleanArray() }
        objectRepo[Array<Boolean>::class(Boolean::class()).type] = { type, past, token -> list(Boolean::class, token, past).toTypedArray() }
        objectRepo[ByteArray::class(Byte::class()).type] = { type, past, token -> list(Byte::class, token, past).toByteArray() }
        objectRepo[Array<Byte>::class(Byte::class()).type] = { type, past, token -> list(Byte::class, token, past).toTypedArray() }
        objectRepo[CharArray::class(Char::class()).type] = { type, past, token -> list(Char::class, token, past).toCharArray() }
        objectRepo[Array<Char>::class(Char::class()).type] = { type, past, token -> list(Char::class, token, past).toTypedArray() }
    }
}