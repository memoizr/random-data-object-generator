package memoizr

import memoizr.roost.AGenericJavaClass
import memoizr.roost.aRandom
import memoizr.roost.noot.User
import memoizr.roost.print
import org.junit.Test
import java.util.*

class footest {
    val userInfo by aRandom<AGenericJavaClass<Set<Map<Int, String>>>>()
    @Test
    fun ffff() {
        val rand = Random(1234)
        rand.nextInt().print()
        rand.nextInt().print()
        rand.nextInt().print()
        rand.nextInt().print()
    }
}

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
