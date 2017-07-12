package memoizr


import com.memoizr.assertk.expect
import com.memoizr.assertk.notNull
import memoizr.roost.*
import org.junit.Test
import kotlin.reflect.KProperty

object CustomizationForTest : Customizer {
    val a by customize<Pair<Int, Int>>().using<Int, Int>(::Pair) { it[3, 4] }
    val b by customize<Pair<String, List<Int>>>().using<String, List<Int>>(::Pair) { it["hey", listOf(5)] }
    val p0 by customize<Param0>().using({Param0}) { Param0 }
    val p1 by customize<Param1<Int>>().using<Int>(::Param1) { it[1] }
    val p2 by customize<Param2<Int, Int>>().using<Int, Int>(::Param2) { it[1, 2] }
    val p3 by customize<Param3<Int, Int, Int>>().using<Int, Int, Int>(::Param3) { it[1, 2, 3] }
    val p4 by customize<Param4<Int, Int, Int, Int>>().using<Int, Int, Int, Int>(::Param4) { it[1, 2, 3, 4] }
    val p5 by customize<Param5<Int, Int, Int, Int, Int>>().using<Int, Int, Int, Int, Int>(::Param5) { it[1, 2, 3, 4, 5] }
    val p6 by customize<Param6<Int, Int, Int, Int, Int, Int>>().using<Int, Int, Int, Int, Int, Int>(::Param6) { it[1, 2, 3, 4, 5, 6] }
}

class CustomizeTest {

    val pairIntInt by aRandom<Pair<Int, Int>>()
    val pairStringListInt by aRandom<Pair<String, List<Int>>>()

    init {
        CustomizationForTest.register()
    }

    @Test
    fun `works with generics`() {
        expect that pairIntInt isEqualTo Pair(3, 4)
        expect that pairStringListInt isEqualTo Pair("hey", listOf(5))
    }

    val p0 by aRandom<Param0>()
    val p1 by aRandom<Param1<Int>>()
    val p2 by aRandom<Param2<Int, Int>>()
    val p3 by aRandom<Param3<Int, Int, Int>>()
    val p4 by aRandom<Param4<Int, Int, Int, Int>>()
    val p5 by aRandom<Param5<Int, Int, Int, Int, Int>>()

    @Test
    fun `works with different arities`() {
        expect that p0 _is notNull
        expect that p1.t1 isEqualTo 1
        expect that p2.t2 isEqualTo 2
        expect that p3.t3 isEqualTo 3
        expect that p4.t4 isEqualTo 4
        expect that p5.t5 isEqualTo 5
    }
}

class customize<T> {
    fun using(fn: () -> T, g: Creator.(() -> T) -> T) = Delegate0(fn, g)
    fun <A> using(fn: (A) -> T, g: Creator.((A) -> T) -> T) = Delegate1(fn, g)
    fun <A, B> using(fn: (A, B) -> T, g: Creator.((A, B) -> T) -> T) = Delegate2(fn, g)
    fun <A, B, C> using(fn: (A, B, C) -> T, g: Creator.((A, B, C) -> T) -> T) = Delegate3(fn, g)
    fun <A, B, C, D> using(fn: (A, B, C, D) -> T, g: Creator.((A, B, C, D) -> T) -> T) = Delegate4(fn, g)
    fun <A, B, C, D, E> using(fn: (A, B, C, D, E) -> T, g: Creator.((A, B, C, D, E) -> T) -> T) = Delegate5(fn, g)
    fun <A, B, C, D, E, F> using(fn: (A, B, C, D, E, F) -> T, g: Creator.((A, B, C, D, E, F) -> T) -> T) = Delegate6(fn, g)
}

class Delegate0<T>(val fn: () -> T, val g: Creator.(() -> T) -> T) {
    operator fun getValue(a: Any, b: KProperty<*>): T {
        objectRepo[b.returnType] = { _, _, token -> Creator(token).g(fn) as Any }
        return null as T
    }
}

class Delegate1<A, T>(val fn: (A) -> T, val g: Creator.((A) -> T) -> T) {
    operator fun getValue(a: Any, b: KProperty<*>): T {
        objectRepo[b.returnType] = { _, _, token -> Creator(token).g(fn) as Any }
        return null as T
    }
}

class Delegate2<A, B, T>(val fn: (A, B) -> T, val g: Creator.((A, B) -> T) -> T) {
    operator fun getValue(a: Any, b: KProperty<*>): T {
        objectRepo[b.returnType] = { _, _, token -> Creator(token).g(fn) as Any }
        return null as T
    }
}

class Delegate3<A, B, C, T>(val fn: (A, B, C) -> T, val g: Creator.((A, B, C) -> T) -> T) {
    operator fun getValue(a: Any, b: KProperty<*>): T {
        objectRepo[b.returnType] = { _, _, token -> Creator(token).g(fn) as Any }
        return null as T
    }
}

class Delegate4<A, B, C, D, T>(val fn: (A, B, C, D) -> T, val g: Creator.((A, B, C, D) -> T) -> T) {
    operator fun getValue(a: Any, b: KProperty<*>): T {
        objectRepo[b.returnType] = { _, _, token -> Creator(token).g(fn) as Any }
        return null as T
    }
}

class Delegate5<A, B, C, D, E, T>(val fn: (A, B, C, D, E) -> T, val g: Creator.((A, B, C, D, E) -> T) -> T) {
    operator fun getValue(a: Any, b: KProperty<*>): T {
        objectRepo[b.returnType] = { _, _, token -> Creator(token).g(fn) as Any }
        return null as T
    }
}

class Delegate6<A, B, C, D, E, F, T>(val fn: (A, B, C, D, E, F) -> T, val g: Creator.((A, B, C, D, E, F) -> T) -> T) {
    operator fun getValue(a: Any, b: KProperty<*>): T {
        objectRepo[b.returnType] = { _, _, token -> Creator(token).g(fn) as Any }
        return null as T
    }
}

interface Customizer {
    fun register() = this::class.java
            .methods
            .filter { it.parameters.isEmpty() && it.name.startsWith("get") }
            .forEach { it.invoke(this) }
}
