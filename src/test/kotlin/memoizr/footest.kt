package memoizr


import memoizr.roost.AGenericJavaClass
import memoizr.roost.Creator
import memoizr.roost.aRandom
import memoizr.roost.noot.SimpleClass
import memoizr.roost.noot.User
import memoizr.roost.print
import org.junit.Test
import java.math.BigDecimal
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

class footest {
    val userInfo by aRandom<AGenericJavaClass<Set<Map<Int, String>>>>()
    val x by aRandom<Iterable<String>>()
    val t by customize<Pair<Int, Int>>().using<Int, Int>(::Pair) { it[any(), any()] }
    val y by customize<SimpleClass>().using(::SimpleClass) { it("") }

    val z by customize<Iterable<String>>().using<String>(::listOf) { it[any()] }

    val q by customize<BigDecimal>().using<Int>(::BigDecimal) { it[any()] }

    @Test
    fun ffff() {
//        a<List<String>>(String::class()).print()
//        a<Pair<String, List<Int>>>(String::class(), List::class(Int::class())).print()
//
//        a<String>()
//        a<SimpleClass>().copy(name = "noo")

//        val aRandoms: Map<String, String> = a(String::class(), String::class())

//        FoBars(a(Int::class()), a()).print()
    }

    data class FoBars(val a: List<Int>, val b: SimpleClass)

}

val Any.t: KClass<*> get() {
    return this::class
}


class customize<T> {

    fun <A> using(fn: (A) -> T, g: Creator.((A) ->T) -> T) = Bars2<A,T>()
    fun <A,B> using(fn: (A, B) -> T, g: Creator.((A, B) ->T) -> T) = Bars<A,B,T>()
    fun <A,B, C> using(fn: (A, B, C) -> T, g: Creator.((A, B, C) -> T) -> T) = Bars<A,B,T>()
}
class Bars2<A,T>{
    operator fun getValue(a: Any, b: KProperty<*>): T? {
        b.returnType.print()
        return null
    }
}
class Bars<A, B, T>{
    operator fun getValue(a: Any, b: KProperty<*>): T? {
        b.returnType.print()
        return null
    }
}

class TestClass<A, B>(o: List<String>, a: A, b: B)


interface UserInfoService {
    fun getUserInfo(): List<UserInfo>
}

class ConcreteUserInfoService : UserInfoService {
    override fun getUserInfo(): List<UserInfo> = listOf()
}

data class UserName(val name: String)
data class Address(val lineOne: String)
data class UserInfo(val age: Int, val addresses: List<Address>)

object Fixtures {
    val aNotLoggedInUser by aRandom<User> { copy(age = 32) }
}

val UserName.notLoggedIn by aRandom<User> { copy(age = 100) }
