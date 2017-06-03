@file:Suppress("UNCHECKED_CAST")

package memoizr.roost

import com.memoizr.assertk.expect
import com.memoizr.assertk.isEqualTo
import com.memoizr.assertk.of
import org.apache.commons.lang3.RandomStringUtils
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
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
        println(aSimpleCompoundClass)
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
}

data class SimpleClass(val name: String)
data class OtherSimpleClass(val otherName: String)
data class SimpleCompoundClass(val simpleClass: SimpleClass, val otherSimpleClass: OtherSimpleClass)
data class NullableClass(val name: String, val nullable: String?)
data class RecursiveClass(val sample: SimpleClass, val nullableClass: NullableClass)
data class ClassWithEnum(val enum: TheEnum)
data class ClassWithBigDecimal(val bigDecimal: BigDecimal)

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

class aRandom<out T> {
    operator fun getValue(hostClass: Any, property: KProperty<*>): T {
        return instantiateClazz(property.returnType.jvmErasure, hostClass::class.java.canonicalName + "::" + property.name) as T
    }

    fun aString(token: String = ""): String {
        println(token)
        fun hashString(string: String): Long = string.toByteArray().map(Byte::toLong).sum()

        return Random(Seed.seed + hashString(token)).let {
            RandomStringUtils.random(Math.max(1, it.nextInt(Seed.maxStringLength)), 0, 59319, true, true, null, it)
        }
    }

    private fun <R : Any> instantiateClazz(klass: KClass<R>, token: String = ""): R {
        return when {
            Creator.objectFactory.contains(klass.java) -> {
                println("hey")
                Creator.objectFactory.get(klass.java)?.invoke() as R
            }
            klass.java.canonicalName == String::class.java.canonicalName -> aString(token) as R
            klass.java.isEnum -> klass.java.enumConstants[Random(Seed.seed).nextInt(klass.java.enumConstants.size)]
            klass == kotlin.String::class -> aString(token) as R
            else -> {
                val constructors = klass.constructors.toList()
                val defaultConstructor = constructors[Random(Seed.seed).nextInt(constructors.size)]
                defaultConstructor.isAccessible = true
                val constructorParameters = defaultConstructor.parameters
                defaultConstructor.call(*(constructorParameters.map {
                    if (it.type.isMarkedNullable && Random(Seed.seed).nextBoolean()) null else instantiateClazz(it.type.jvmErasure, "$token::${it.type.javaType.typeName}")
                }).toTypedArray())
            }
        }
    }
}
