@file:Suppress("UNCHECKED_CAST")

package memoizr.roost

import com.memoizr.assertk.*
import memoizr.customize
import memoizr.roost.noot.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.util.*

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

    @Before
    fun setUp() {
        Seed.testing = true
    }

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
        val groupedValues: List<Pair<Boolean, Int>> = (1..1000).map {
            Seed.seed = Random().nextLong()
            Pair(aNullableClass.nullable == null, 1)
        }.groupBy { it.first }.map { (k, v) -> Pair(k, v.count()) }

        expect that groupedValues[0].second isCloseTo groupedValues[1].second withinPercentage 15
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
        val enums = (1..1000).map {
            Seed.seed = Random().nextLong()
            aClassWithEnum.enum
        }.groupBy { a -> a }

        enums.forEach { _, u ->
            expect that u.size isCloseTo 250 withinPercentage 15
        }
        expect that enums.size isEqualTo 4
        expect that aClassWithEnum.enum isInstance of<TheEnum>()
    }

    val x by customize<BigDecimal>().using<Long>(::BigDecimal) { it[any()] }
    @Before
    fun xx() {
        x == null
    }

    @Test
    fun `it allows to customize object creation`() {
        x
        expect that aClassWithBigDecimal isInstance of<ClassWithBigDecimal>()
    }

    @Test
    fun `it generates an empty list`() {
        expect that aJavaClassWithList.list isEqualTo aJavaClassWithList.list
        expect that aClassWithList.list isEqualTo aClassWithList.list
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

    val aRandomList by aRandomListOf<SimpleClass>()
    val aRandomListOfList by aRandomListOf<List<List<List<SimpleClass>>>>()
    val aRandomListSize10 by aRandomListOf<SimpleClass>(size = 10)

    @Test
    fun `creates a random list`() {
        expect that aRandomListOfList.size isGreaterThan 0
        expect that aRandomList.size isGreaterThan 0
        expect that aRandomListSize10.size isEqualTo 10
    }

    val aClassWithInterface by aRandom<AClassWithInterface>()

    @Test
    fun `it copes with interfaces in same package`() {
        expect that aClassWithInterface.inter isInstance of<AnInterface>()
    }

    val aClassWithObject by aRandom<AClassWithObject>()

    @Test
    fun `it copes with objects`() {
        expect that aClassWithObject.anObject isEqualTo AnObject
    }

    val aPair by aRandom<Pair<String, Int>>()

    @Test
    fun `creates instances of generic classes`() {
        expect that aPair.first isInstance of<String>()
        expect that aPair.second isInstance of<Int>()
    }

    val aSet by aRandom<Set<Set<String>>>()

    @Test
    fun `creates a set`() {
        expect that aSet.size isGreaterThan 0
    }

    val aMap by aRandom<Map<Map<Interface1, ClassWithList>, Map<String, Set<SimpleClass>>>>()

    @Test
    fun `create a map`() {
        expect that aMap.size isGreaterThan 0
    }

    val aSealedClass by aRandom<SealedClass>()

    @Test
    fun `creates sealed classes`() {
        expect that aSealedClass isInstance of<SealedClass>()
    }

    val aFooBar by aRandom<Fooed>()

    @Test
    fun `goes fast with interfaces`() {
        expect that aFooBar _is notNull
    }

    val anUri by aRandom<File>()

    @Test
    fun `creates URI`() {
        expect that anUri _is notNull
    }

    val anArrayClass by aRandom<ClassWithArrays>()

    @Test
    fun `it works with arrays`() {
        expect that anArrayClass is_ notNull
    }

    val interfaceImpl by aRandom<InterfaceWithNoImplementations>()

    @Test
    fun `it works with interfaces with no implementations`() {
        expect that interfaceImpl.listOfObjects().first() isInstance of<SimpleClass>()
        expect that interfaceImpl.listOfObjects("").first() isInstance of<String>()
        expect that interfaceImpl.arrayOfObjects().first() isInstance of<SimpleClass>()
        expect that interfaceImpl.string() isInstance of<String>()
        expect that interfaceImpl.simpleClass isInstance of<SimpleClass>()
        expect that interfaceImpl isEqualTo interfaceImpl
    }

    val problematicClass by aRandom<ProblematicConstructorClass>()

    @Test
    fun `throws meaningful exception when instantiation fails`() {
        expect thatThrownBy {problematicClass} hasMessageContaining
                "Something went wrong" hasMessageContaining
                "with values" hasMessageContaining
                "y=" hasMessageContaining
                "x=" hasCauseExactlyInstanceOf
                Exception::class.java isInstance of<CreationException>()
    }
}
