@file:Suppress("UNCHECKED_CAST")

package memoizr.roost

import com.memoizr.assertk.expect
import com.memoizr.assertk.isEqualTo
import com.memoizr.assertk.of
import org.apache.commons.lang3.RandomStringUtils
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class RandomGenerationTest {
    val aSimpleClass by aRandom<SimpleClass>()
    val anotherSimpleClass by aRandom<SimpleClass>()
    val aNullableClass by aRandom<NullableClass>()
    val aRecursiveClass by aRandom<RecursiveClass>()
    val anotherRecursiveClass by aRandom<RecursiveClass>()
    val aClassWithEnum by aRandom<ClassWithEnum>()
    val aClassWithBigDecimal by aRandom<ClassWithBigDecimal>()
    val aSimpleCompoundClass by aRandom<SimpleCompoundClass>()
    val aClassWithList by aRandom<ClassWithList>()
    val aClassWithMutableList by aRandom<ClassWithMutableList>()
    val aJavaClassWithList by aRandom<JavaClassWithList>()
    val aClassWithPrimitives by aRandom<ClassWithPrimitives>()

    @Test
    fun `creates an arbitrary data class`() {
        expect that aSimpleClass isInstance of<SimpleClass>()
        expect that aSimpleClass.name.length isBetween 1..20
        expect that aSimpleClass.name isEqualTo aSimpleClass.name
    }

    @Test
    fun `returns different results for different seeds`() {
        (1..100).map {
            Seed.seed = Random().nextLong()
            aSimpleClass.name
        }.toSet().size isEqualTo 100
    }

    @Test
    fun `returns null or not null randomly for nullable values`() {
        val groupedValues: List<Pair<Boolean, Int>> = (1..10000).map {
            Seed.seed = Random().nextLong()
            Pair(aNullableClass.nullable == null, 1)
        }.groupBy { it.first }.map { (k, v) -> Pair(k, v.count()) }

        expect that groupedValues[0].second isCloseTo groupedValues[1].second withinPercentage 5
    }

    @Test
    fun `it generates different values for each parameter`() {
        expect that aSimpleCompoundClass.simpleClass.name isEqualTo aSimpleCompoundClass.simpleClass.name
        expect that aSimpleCompoundClass.simpleClass.name isNotEqualTo aSimpleCompoundClass.otherSimpleClass.otherName
    }

    @Test
    fun `it creates objects recursively`() {
        expect that aRecursiveClass isInstance of<RecursiveClass>()
        expect that aRecursiveClass.sample isEqualTo aRecursiveClass.sample
    }

    @Test
    fun `it creates different objects for different property values`() {
        expect that aSimpleClass isInstance of<SimpleClass>()
        expect that anotherSimpleClass isInstance of<SimpleClass>()
        expect that aSimpleClass isNotEqualTo anotherSimpleClass
        expect that anotherSimpleClass.name isEqualTo anotherSimpleClass.name
        expect that anotherRecursiveClass.sample isEqualTo anotherRecursiveClass.sample
    }

    @Test
    fun `it generates random enums`() {
        val enums = (1..10000).map {
            Seed.seed = Random().nextLong()
            aClassWithEnum.enum
        }.groupBy { a -> a }

        enums.forEach { _, u ->
            expect that u.size isCloseTo 2500 withinPercentage 5
        }
        expect that enums.size isEqualTo 4
        expect that aClassWithEnum.enum isInstance of<TheEnum>()
    }

    @Test
    fun `it allows to customize object creation`() {
        Creator.objectFactory.put(BigDecimal::class.java, { -> BigDecimal(1) })

        expect that aClassWithBigDecimal isInstance of<ClassWithBigDecimal>()
    }

    @Test
    fun `it generates an empty list`() {
        expect that aJavaClassWithList.list isEqualTo aJavaClassWithList.list
        expect that aClassWithList.list isEqualTo aClassWithList.list
//        expect that aClassWithList.list.size isEqualTo Random(Seed.seed).nextInt(10) + 1
        expect that aClassWithMutableList.list.plus("hey") contains mutableListOf("hey")
    }

    @Test
    fun `covers all the primitives`() {
        expect that aClassWithPrimitives isEqualTo aClassWithPrimitives
    }

    val aSimpleClassCustomized by aRandom<SimpleClass> { copy(name = "foo") }

    @Test
    fun `objects can be customized`() {
        expect that aSimpleClassCustomized.name isEqualTo "foo"
    }

    val aCyclicClass by aRandom<CyclicClass>()
    @Test
    fun `does not allow for recursive classes`() {
        expect thatThrownBy { aCyclicClass } hasMessageContaining "cyclic"
    }
}

class CyclicException : Throwable("Illegal cyclic dependency")

data class SimpleClass(val name: String)
data class OtherSimpleClass(val otherName: String)
data class SimpleCompoundClass(val simpleClass: SimpleClass, val otherSimpleClass: OtherSimpleClass)
data class NullableClass(val name: String, val nullable: String?)
data class RecursiveClass(val sample: SimpleClass, val nullableClass: NullableClass)
data class ClassWithEnum(val enum: TheEnum)
data class ClassWithBigDecimal(val bigDecimal: BigDecimal)
data class ClassWithList(val list: List<String>)
data class ClassWithMutableList(val list: MutableList<String>)
data class CyclicClass(val cycles: List<CyclicClass>, val cycle: CyclicClass)
data class ClassWithPrimitives(
        val int: Int,
        val short: Short,
        val long: Long,
        val float: Float,
        val double: Double,
        val boolean: Boolean,
        val byte: Byte,
        val char: Char
)

enum class TheEnum {
    One, Two, Three, Four
}

object Creator {
    val objectFactory = mutableMapOf<Class<out Any>, () -> Any>()
}

object Seed {
    var seed: Long = Random().nextLong()
    val maxStringLength = 20
}

class aRandom<out T>(private val custom: T.() -> T = { this }) {
    operator fun getValue(hostClass: Any, property: KProperty<*>): T {
        return (instantiateClazz(property.returnType.jvmErasure, hostClass::class.java.canonicalName + "::" + property.name) as T).let { custom(it) }
    }

    private fun hashString(string: String): Long = string.toByteArray().map(Byte::toLong).sum()

    fun aString(token: String = ""): String {
        return Random(getSeed(token)).let {
            RandomStringUtils.random(Math.max(1, it.nextInt(Seed.maxStringLength)), 0, 59319, true, true, null, it)
        }
    }

    fun aChar(token: String = ""): Char = Random(getSeed(token)).nextInt(59319).toChar()
    fun anInt(token: String = ""): Int = Random(getSeed(token)).nextInt()
    fun aLong(token: String = ""): Long = Random(getSeed(token)).nextLong()
    fun aDouble(token: String = ""): Double = Random(getSeed(token)).nextDouble()
    fun aShort(token: String = ""): Short = Random(getSeed(token)).nextInt(Short.MAX_VALUE.toInt()).toShort()
    fun aFloat(token: String = ""): Float = Random(getSeed(token)).nextFloat()
    fun aByte(token: String = ""): Byte = Random(getSeed(token)).nextInt(255).toByte()

    fun aBoolean(token: String = ""): Boolean {
        return Random(getSeed(token)).nextBoolean()
    }

    private fun <R : Any> aList(typeParameter: KTypeProjection, token: String, past: Set<KClass<*>>): R {
        val klass = typeParameter.type!!.jvmErasure
        if (past.contains(klass)) throw CyclicException()
        return (0..Random(getSeed(token)).nextInt(10)).map { instantiateClazz(klass, "$token::$it") } as R
    }

    private fun <R : Any> instantiateClazz(klass: KClass<R>, token: String = "", typeParameters: List<KTypeProjection> = emptyList(), past: Set<KClass<*>> = emptySet()): R {
        if (past.contains(klass)) throw CyclicException()
        return when {
            Creator.objectFactory.contains(klass.java) -> {
                Creator.objectFactory.get(klass.java)?.invoke() as R
            }
            klass.java.canonicalName == String::class.java.canonicalName -> aString(token) as R
            klass.java.isEnum -> klass.java.enumConstants[Random(getSeed(token)).nextInt(klass.java.enumConstants.size)]
            klass == kotlin.collections.List::class -> aList(typeParameters.first(), token, past.plus(klass))
            klass == kotlin.String::class -> aString(token) as R
            klass == kotlin.Byte::class -> aByte(token) as R
            klass == kotlin.Int::class -> anInt(token) as R
            klass == kotlin.Long::class -> aLong(token) as R
            klass == kotlin.Double::class -> aDouble(token) as R
            klass == kotlin.Short::class -> aShort(token) as R
            klass == kotlin.Short::class -> aShort(token) as R
            klass == kotlin.Float::class -> aFloat(token) as R
            klass == kotlin.Boolean::class -> aBoolean(token) as R
            klass == kotlin.Char::class -> aChar(token) as R
            else -> {
                val constructors = klass.constructors.toList()
                val defaultConstructor: KFunction<R> = constructors[Random(getSeed(token)).nextInt(constructors.size)]
                defaultConstructor.isAccessible = true
                val constructorParameters: List<KParameter> = defaultConstructor.parameters
                defaultConstructor.call(*(constructorParameters.map {
                    val typeArguments: List<KTypeProjection> = it.type.arguments
                    if (it.type.isMarkedNullable && Random(getSeed(token)).nextBoolean()) null else {
                        instantiateClazz(it.type.jvmErasure, "$token::${it.type.javaType.typeName}", typeArguments, past.plus(klass))
                    }
                }).toTypedArray())
            }
        }
    }

    private fun getSeed(token: String) = Seed.seed + hashString(token)
}
