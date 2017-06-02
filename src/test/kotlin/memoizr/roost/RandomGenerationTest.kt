@file:Suppress("UNCHECKED_CAST")

package memoizr.roost

import com.memoizr.assertk.expect
import com.memoizr.assertk.isEqualTo
import com.memoizr.assertk.of
import org.apache.commons.lang3.RandomStringUtils
import org.junit.Test
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

class RandomGenerationTest {
    val aSimpleClass by aRandom<SimpleClass>()
    val anotherSimpleClass by aRandom<SimpleClass>()
    val aNullableClass by aRandom<NullableClass>()
    val aRecursiveClass by aRandom<RecursiveClass>()
    val anotherRecursiveClass by aRandom<RecursiveClass>()

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
    fun `it creates objects recursively`() {
        expect that aRecursiveClass isInstance of<RecursiveClass>()
        expect that aRecursiveClass.sample isEqualTo aRecursiveClass.sample
    }
    
    @Test
    fun `it creates different objects for different property names`() {
        expect that aSimpleClass isInstance of<SimpleClass>()
        expect that anotherSimpleClass isInstance of<SimpleClass>()
        expect that aSimpleClass isNotEqualTo anotherSimpleClass
        expect that anotherSimpleClass.name isEqualTo anotherSimpleClass.name
        expect that anotherRecursiveClass.sample isEqualTo anotherRecursiveClass.sample
    }
}

data class SimpleClass(val name: String)
data class NullableClass(val name: String, val nullable: String?)
data class RecursiveClass(val sample: SimpleClass, val nullableClass: NullableClass)

internal object Seed {
    var seed: Long = Random().nextLong()
    val maxStringLength = 20
}

class aRandom<out T> {
    operator fun getValue(hostClass: Any, property: KProperty<*>): T =
            instantiateClazz(property.returnType.jvmErasure, hostClass::class.java.canonicalName + "::" + property.name) as T

    fun aString(token: String = "") = Random(Seed.seed + hashString(token)).let {
        RandomStringUtils.random(Math.max(1, it.nextInt(Seed.maxStringLength)), 0, 59319, true, true, null, it)
    }

    private fun <R: Any> instantiateClazz(klass: KClass<R>, token: String = ""): R {
        return when {
            klass.java.canonicalName == String::class.java.canonicalName -> aString(token) as R
            klass == kotlin.String::class -> aString(token) as R
            else -> {
                val constructors = klass.constructors.toList()
                val defaultConstructor = constructors[Random(Seed.seed).nextInt(constructors.size)]
                defaultConstructor.isAccessible = true
                val constructorParameters = defaultConstructor.parameters
                defaultConstructor.call(*(constructorParameters.map {
                    if (it.type.isMarkedNullable && Random(Seed.seed).nextBoolean()) null else instantiateClazz(it.type.jvmErasure, "$token::${it.javaClass.canonicalName}")
                }).toTypedArray()) as R
            }
        }
    }

    private fun hashString(string: String): Long {
        return string.toByteArray().map(Byte::toLong).sum()
    }
}
