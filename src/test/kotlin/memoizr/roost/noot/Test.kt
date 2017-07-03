package memoizr.roost.noot

import memoizr.roost.aRandom
import memoizr.roost.create
import memoizr.roost.print
import memoizr.roost.some
import org.junit.Test
import java.io.File
import java.io.Serializable
import kotlin.reflect.KFunction1

class Test {
    val clip by aRandom<Clip>()
    val aUser by aRandom<String>()

    @Test
    fun user() {
        aUser.print()
    }

    @Test
    fun aTest() {
        clip.print()
    }

    @Test
    fun ff() {

//        List::class.createType(listOf(KTypeProjection(OUT, String::class.createType()))).print()

//        List::class(OUT, String::class).print()

//        listKType<String>()
//        ClipId(a(String::class())).print()
//        List::class of String::class
//        ClassWithList(a(listKType<String>())).print()
//        ClassWithList(a(Fooed::class of SimpleClass::class)).print()


        val kFunction1: KFunction1<@ParameterName(name = "name") String, SimpleClass> = ::SimpleClass

        kFunction1.create(a = "").print()

        ::Size.create(some(), some()).print()

        ::SimpleCompoundClass.create(::SimpleClass.create().copy(""), some()).print()
    }

}

data class User(
        val age: Int,
        val name: String,
        val addresses: List<Address>,
        val bored: Boolean,
        val height: Float)

data class Address(val lineOne: String, val lineTwo: String, val postCode: String)

data class Z(val y: String) : Interface1

data class Clip(
        val clipId: ClipId,
        val thumbnail: Image,
        val gif: Gif,
        val video: Video,
        val badge: Badge,
        val category: Category,
        val tags: List<Tag>,
        val rightsHolder: RightsHolder) : Serializable

data class Size(val width: Int, val height: Int) : Serializable {
    fun heightAspect() = height.toFloat() / width
    fun widthAspect() = width.toFloat() / height
}

data class ClipId(val id: String) : Serializable
data class RightsHolder(val name: String) : Serializable

interface Media : Serializable {
    val size: Size
}

interface Video : Media
interface Image : Media
interface Gif : Image
data class UriVideo(val uri: String, override val size: Size) : Video
data class FileVideo(val file: File, override val size: Size) : Video
data class UriImage(val uri: String, override val size: Size) : Image
data class UriGif(val uri: String, override val size: Size) : Gif

data class CategoryId(val id: String) : Serializable
data class Category(val categoryId: CategoryId, val name: String) : Serializable

enum class Badge { NONE, HOT, NEW }

sealed class Tag : Query {
    abstract val category: Category
    abstract val name: String
}

data class TextTag(override val category: Category, override val name: String) : Tag() {
    override val query = name
}

data class ImageTag(override val category: Category, override val name: String, val image: Image) : Tag() {
    override val query = name
}

interface Query : Serializable {
    val query: String

    fun isNotEmpty(): Boolean = query.isNotEmpty()
    fun isEmpty(): Boolean = query.isEmpty()
}
