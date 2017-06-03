package memoizr.roost

import org.junit.Test

class Test {

    val user by aRandom<User>()

    @Test
    fun aTest() {
        println(user)
    }
}

data class User(
        val age: Int,
        val name: String,
        val addresses: List<Address>,
        val bored: Boolean,
        val friend: User,
        val height: Float)

data class Address(val lineOne: String, val lineTwo: String, val postCode: String)
