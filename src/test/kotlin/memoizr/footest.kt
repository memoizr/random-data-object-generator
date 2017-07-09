package memoizr

import memoizr.roost.AGenericJavaClass
import memoizr.roost.aRandom
import memoizr.roost.noot.User
import memoizr.roost.print
import org.junit.Test

class footest {
    val userInfo by aRandom<AGenericJavaClass<Set<Map<Int, String>>>>()
    val x by aRandom<Iterable<String>>()
    @Test
    fun ffff() {
        x.print()

//        val constructor = URI::class.java.constructors[2].print()
//        Reflections(ConfigurationBuilder()
//                .setUrls(ClasspathHelper.forClass(URI::class.java))
//                .setScanners(
//                        SubTypesScanner(false),
//                        MethodParameterScanner(),
//                        MethodParameterNamesScanner())
//        ).save("classes.xml")
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
