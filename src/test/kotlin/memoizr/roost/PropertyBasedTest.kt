package memoizr.roost

import com.memoizr.assertk.expect
import com.memoizr.assertk.is_
import com.memoizr.assertk.notNull
import com.memoizr.assertk.of
import memoizr.roost.noot.SimpleClass
import org.junit.Test

class PropertyBasedTest {
    @Test
    fun `it performs the test 100 times by default`() {
        val res = mutableSetOf<SimpleClass>()
        forAll {
            val simpleClass = a<SimpleClass>()
            res.add(simpleClass)
        }

        expect that res.size isEqualTo 100
    }

    @Test
    fun `it performs the test a custom number of times`() {
        val res = mutableSetOf<SimpleClass>()
        forAll(42) {
            val simpleClass = a<SimpleClass>()
            res.add(simpleClass)
        }

        expect that res.size isEqualTo 42
    }

    @Test
    fun `it works with generics`() {
        forAll {
            expect that a<List<String>>(String::class()).first() isInstance of<String>()
        }
    }

    @Test
    fun `it provides different values`() {
        forAll {
            expect that a<SimpleClass>() isNotEqualTo a<SimpleClass>()
            expect that a<Param1<Int>>(Int::class()) isNotEqualTo a<Param1<Int>>(Int::class())
            expect that a<Param2<Int, Int>>(Int::class(), Int::class()) isNotEqualTo a<Param2<Int, Int>>(Int::class(), Int::class())
            expect that a<Param3<Int, Int, Int>>(Int::class(), Int::class(), Int::class()) isNotEqualTo a<Param3<Int, Int, Int>>(Int::class(), Int::class(), Int::class())
            expect that a<Param4<Int, Int, Int, Int>>(Int::class(), Int::class(), Int::class(), Int::class()) isNotEqualTo a<Param4<Int, Int, Int, Int>>(Int::class(), Int::class(), Int::class(), Int::class())
            expect that a<Param5<Int, Int, Int, Int, Int>>(Int::class(), Int::class(), Int::class(), Int::class(), Int::class()) isNotEqualTo a<Param5<Int, Int, Int, Int, Int>>(Int::class(), Int::class(), Int::class(), Int::class(), Int::class())
        }
    }

    @Test
    fun `it works for different arities`() {
        forAll {
            expect that a<Param0>() is_ notNull
            expect that a<Param1<Int>>(Int::class()) is_ notNull
            expect that a<Param2<Int, Int>>(Int::class(), Int::class()) is_ notNull
            expect that a<Param3<Int, Int, Int>>(Int::class(), Int::class(), Int::class()) is_ notNull
            expect that a<Param4<Int, Int, Int, Int>>(Int::class(), Int::class(), Int::class(), Int::class()) is_ notNull
            expect that a<Param5<Int, Int, Int, Int, Int>>(Int::class(), Int::class(), Int::class(), Int::class(), Int::class()) is_ notNull
        }
    }
}

object Param0
data class Param1<T1>(val t1: T1)
data class Param2<T1, T2>(val t1: T1, val t2: T2)
data class Param3<T1, T2, T3>(val t1: T1, val t2: T2, val t3: T3)
data class Param4<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)
data class Param5<T1, T2, T3, T4, T5>(val t1: T1, val t2: T2, val t3: T3, val t4: T4, val t5: T5)
