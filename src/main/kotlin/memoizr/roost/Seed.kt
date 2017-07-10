package memoizr.roost

import java.io.File
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.createType
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

        objectFactory.put(kotlin.String::class, { type, past, token -> aString(token) })
        objectFactory.put(kotlin.Byte::class, { type, past, token -> aByte(token) })
        objectFactory.put(kotlin.Int::class, { type, past, token -> anInt(token) })
        objectFactory.put(kotlin.Long::class, { type, past, token -> aLong(token) })
        objectFactory.put(kotlin.Double::class, { type, past, token -> aDouble(token) })
        objectFactory.put(kotlin.Short::class, { type, past, token -> aShort(token) })
        objectFactory.put(kotlin.Float::class, { type, past, token -> aFloat(token) })
        objectFactory.put(kotlin.Boolean::class, { type, past, token -> aBoolean(token) })
        objectFactory.put(kotlin.Char::class, { type, past, token -> aChar(token) })

        objectFactory.put(File::class, { type, past, token -> File(aString(token)) })

        objectFactory.put(List::class, { type, past, token -> list(type, token, past) })
        objectFactory.put(Set::class, { type, past, token -> list(type, token, past).toSet() })
        objectFactory.put(kotlin.collections.Map::class, { type, past, token ->
            list(type, token, past).map { Pair(it, instantiateClass(type.arguments[1].type!!, token)) }.toMap()
        })

        objectFactory.put(IntArray::class, { type, past, token -> list(Int::class, token, past).toIntArray() })
        objectFactory.put(Array<Int>::class, { type, past, token -> list(Int::class, token, past).toTypedArray() })
        objectFactory.put(ShortArray::class, { type, past, token -> list(Short::class, token, past).toShortArray() })
        objectFactory.put(Array<Short>::class, { type, past, token -> list(Short::class, token, past).toTypedArray() })
        objectFactory.put(LongArray::class, { type, past, token -> list(Long::class, token, past).toLongArray() })
        objectFactory.put(Array<Long>::class, { type, past, token -> list(Long::class, token, past).toTypedArray() })
        objectFactory.put(FloatArray::class, { type, past, token -> list(Float::class, token, past).toFloatArray() })
        objectFactory.put(Array<Float>::class, { type, past, token -> list(Float::class, token, past).toTypedArray() })
        objectFactory.put(DoubleArray::class, { type, past, token -> list(Double::class, token, past).toDoubleArray() })
        objectFactory.put(Array<Double>::class, { type, past, token -> list(Double::class, token, past).toTypedArray() })
        objectFactory.put(BooleanArray::class, { type, past, token -> list(Boolean::class, token, past).toBooleanArray() })
        objectFactory.put(Array<Boolean>::class, { type, past, token -> list(Boolean::class, token, past).toTypedArray() })
        objectFactory.put(ByteArray::class, { type, past, token -> list(Byte::class, token, past).toByteArray() })
        objectFactory.put(Array<Byte>::class, { type, past, token -> list(Byte::class, token, past).toTypedArray() })
        objectFactory.put(CharArray::class, { type, past, token -> list(Char::class, token, past).toCharArray() })
        objectFactory.put(Array<Char>::class, { type, past, token -> list(Char::class, token, past).toTypedArray() })
    }
}