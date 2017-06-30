@file:Suppress("UNCHECKED_CAST")

package memoizr.roost

import com.memoizr.assertk.expect
import com.memoizr.assertk.isEqualTo
import com.memoizr.assertk.notNull
import com.memoizr.assertk.of
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

    @Test
    fun `it allows to customize object creation`() {
        Creator.objectFactory.put(BigDecimal::class.java, { -> BigDecimal(1) })

        expect that aClassWithBigDecimal isInstance of<ClassWithBigDecimal>()
    }

    @Test
    fun `it generates an empty list`() {
        expect that aJavaClassWithList.list isEqualTo aJavaClassWithList.list
        expect that aClassWithList.list isEqualTo aClassWithList.list
//        expect that aClassWithList.list.size isEqualTo Random(Seed.seed).nextInt(5) + 1
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
}

sealed class SealedClass
data class One(val x: String) : SealedClass()
data class Two(val x: String) : SealedClass()

interface Interface1
data class Impl1Interface1(val x: String) : Interface1
data class Impl2Interface1(val x: String) : Interface1
data class Impl3Interface1(val x: String) : Interface1
data class Impl4Interface1(val x: String) : Interface1
data class Impl5Interface1(val x: String) : Interface1
data class Impl6Interface1(val x: String) : Interface1
data class Impl7Interface1(val x: String) : Interface1
data class Impl8Interface1(val x: String) : Interface1
data class Impl9Interface1(val x: String) : Interface1
data class Impl10Interface1(val x: String) : Interface1
data class Impl11Interface1(val x: String) : Interface1
data class Impl12Interface1(val x: String) : Interface1
data class Impl13Interface1(val x: String) : Interface1
data class Impl14Interface1(val x: String) : Interface1
data class Impl15Interface1(val x: String) : Interface1
data class Impl16Interface1(val x: String) : Interface1
data class Impl17Interface1(val x: String) : Interface1
data class Impl18Interface1(val x: String) : Interface1
data class Impl19Interface1(val x: String) : Interface1

interface Interface2
data class Impl1Interface2(val x: String) : Interface2
data class Impl2Interface2(val x: String) : Interface2
data class Impl3Interface2(val x: String) : Interface2
data class Impl4Interface2(val x: String) : Interface2
data class Impl5Interface2(val x: String) : Interface2
data class Impl6Interface2(val x: String) : Interface2
data class Impl7Interface2(val x: String) : Interface2
data class Impl8Interface2(val x: String) : Interface2
data class Impl9Interface2(val x: String) : Interface2
data class Impl10Interface2(val x: String) : Interface2
data class Impl11Interface2(val x: String) : Interface2
data class Impl12Interface2(val x: String) : Interface2
data class Impl13Interface2(val x: String) : Interface2
data class Impl14Interface2(val x: String) : Interface2
data class Impl15Interface2(val x: String) : Interface2
data class Impl16Interface2(val x: String) : Interface2
data class Impl17Interface2(val x: String) : Interface2
data class Impl18Interface2(val x: String) : Interface2
data class Impl19Interface2(val x: String) : Interface2
data class Impl20Interface2(val x: String) : Interface2

interface Interface3
data class Impl1Interface3(val x: String) : Interface3
data class Impl2Interface3(val x: String) : Interface3
data class Impl3Interface3(val x: String) : Interface3
data class Impl4Interface3(val x: String) : Interface3
data class Impl5Interface3(val x: String) : Interface3
data class Impl6Interface3(val x: String) : Interface3
data class Impl7Interface3(val x: String) : Interface3
data class Impl8Interface3(val x: String) : Interface3
data class Impl9Interface3(val x: String) : Interface3
data class Impl10Interface3(val x: String) : Interface3
data class Impl11Interface3(val x: String) : Interface3
data class Impl12Interface3(val x: String) : Interface3
data class Impl13Interface3(val x: String) : Interface3
data class Impl14Interface3(val x: String) : Interface3
data class Impl15Interface3(val x: String) : Interface3
data class Impl16Interface3(val x: String) : Interface3
data class Impl17Interface3(val x: String) : Interface3
data class Impl18Interface3(val x: String) : Interface3
data class Impl19Interface3(val x: String) : Interface3

interface Interface4
data class Impl1Interface4(val x: String) : Interface4
data class Impl2Interface4(val x: String) : Interface4
data class Impl3Interface4(val x: String) : Interface4
data class Impl4Interface4(val x: String) : Interface4
data class Impl5Interface4(val x: String) : Interface4
data class Impl6Interface4(val x: String) : Interface4
data class Impl7Interface4(val x: String) : Interface4
data class Impl8Interface4(val x: String) : Interface4
data class Impl9Interface4(val x: String) : Interface4
data class Impl10Interface4(val x: String) : Interface4
data class Impl11Interface4(val x: String) : Interface4
data class Impl12Interface4(val x: String) : Interface4
data class Impl13Interface4(val x: String) : Interface4
data class Impl14Interface4(val x: String) : Interface4
data class Impl15Interface4(val x: String) : Interface4
data class Impl16Interface4(val x: String) : Interface4
data class Impl17Interface4(val x: String) : Interface4
data class Impl18Interface4(val x: String) : Interface4
data class Impl19Interface4(val x: String) : Interface4

interface Interface5
data class Impl1Interface5(val x: String) : Interface5
data class Impl2Interface5(val x: String) : Interface5
data class Impl3Interface5(val x: String) : Interface5
data class Impl4Interface5(val x: String) : Interface5
data class Impl5Interface5(val x: String) : Interface5
data class Impl6Interface5(val x: String) : Interface5
data class Impl7Interface5(val x: String) : Interface5
data class Impl8Interface5(val x: String) : Interface5
data class Impl9Interface5(val x: String) : Interface5
data class Impl10Interface5(val x: String) : Interface5
data class Impl11Interface5(val x: String) : Interface5
data class Impl12Interface5(val x: String) : Interface5
data class Impl13Interface5(val x: String) : Interface5
data class Impl14Interface5(val x: String) : Interface5
data class Impl15Interface5(val x: String) : Interface5
data class Impl16Interface5(val x: String) : Interface5
data class Impl17Interface5(val x: String) : Interface5
data class Impl18Interface5(val x: String) : Interface5
data class Impl19Interface5(val x: String) : Interface5

interface Interface6
data class Impl1Interface6(val x: String) : Interface6
data class Impl2Interface6(val x: String) : Interface6
data class Impl3Interface6(val x: String) : Interface6
data class Impl4Interface6(val x: String) : Interface6
data class Impl5Interface6(val x: String) : Interface6
data class Impl6Interface6(val x: String) : Interface6
data class Impl7Interface6(val x: String) : Interface6
data class Impl8Interface6(val x: String) : Interface6
data class Impl9Interface6(val x: String) : Interface6
data class Impl10Interface6(val x: String) : Interface6
data class Impl11Interface6(val x: String) : Interface6
data class Impl12Interface6(val x: String) : Interface6
data class Impl13Interface6(val x: String) : Interface6
data class Impl14Interface6(val x: String) : Interface6
data class Impl15Interface6(val x: String) : Interface6
data class Impl16Interface6(val x: String) : Interface6
data class Impl17Interface6(val x: String) : Interface6
data class Impl18Interface6(val x: String) : Interface6
data class Impl19Interface6(val x: String) : Interface6

interface Interface7
data class Impl1Interface7(val x: String) : Interface7
data class Impl2Interface7(val x: String) : Interface7
data class Impl3Interface7(val x: String) : Interface7
data class Impl4Interface7(val x: String) : Interface7
data class Impl5Interface7(val x: String) : Interface7
data class Impl6Interface7(val x: String) : Interface7
data class Impl7Interface7(val x: String) : Interface7
data class Impl8Interface7(val x: String) : Interface7
data class Impl9Interface7(val x: String) : Interface7
data class Impl10Interface7(val x: String) : Interface7
data class Impl11Interface7(val x: String) : Interface7
data class Impl12Interface7(val x: String) : Interface7
data class Impl13Interface7(val x: String) : Interface7
data class Impl14Interface7(val x: String) : Interface7
data class Impl15Interface7(val x: String) : Interface7
data class Impl16Interface7(val x: String) : Interface7
data class Impl17Interface7(val x: String) : Interface7
data class Impl18Interface7(val x: String) : Interface7
data class Impl19Interface7(val x: String) : Interface7

interface Interface8
data class Impl1Interface8(val x: String) : Interface8
data class Impl2Interface8(val x: String) : Interface8
data class Impl3Interface8(val x: String) : Interface8
data class Impl4Interface8(val x: String) : Interface8
data class Impl5Interface8(val x: String) : Interface8
data class Impl6Interface8(val x: String) : Interface8
data class Impl7Interface8(val x: String) : Interface8
data class Impl8Interface8(val x: String) : Interface8
data class Impl9Interface8(val x: String) : Interface8
data class Impl10Interface8(val x: String) : Interface8
data class Impl11Interface8(val x: String) : Interface8
data class Impl12Interface8(val x: String) : Interface8
data class Impl13Interface8(val x: String) : Interface8
data class Impl14Interface8(val x: String) : Interface8
data class Impl15Interface8(val x: String) : Interface8
data class Impl16Interface8(val x: String) : Interface8
data class Impl17Interface8(val x: String) : Interface8
data class Impl18Interface8(val x: String) : Interface8
data class Impl19Interface8(val x: String) : Interface8

interface Interface9
data class Impl1Interface9(val x: String) : Interface9
data class Impl2Interface9(val x: String) : Interface9
data class Impl3Interface9(val x: String) : Interface9
data class Impl4Interface9(val x: String) : Interface9
data class Impl5Interface9(val x: String) : Interface9
data class Impl6Interface9(val x: String) : Interface9
data class Impl7Interface9(val x: String) : Interface9
data class Impl8Interface9(val x: String) : Interface9
data class Impl9Interface9(val x: String) : Interface9
data class Impl10Interface9(val x: String) : Interface9
data class Impl11Interface9(val x: String) : Interface9
data class Impl12Interface9(val x: String) : Interface9
data class Impl13Interface9(val x: String) : Interface9
data class Impl14Interface9(val x: String) : Interface9
data class Impl15Interface9(val x: String) : Interface9
data class Impl16Interface9(val x: String) : Interface9
data class Impl17Interface9(val x: String) : Interface9
data class Impl18Interface9(val x: String) : Interface9
data class Impl19Interface9(val x: String) : Interface9

interface Interface10
data class Impl1Interface10(val x: String) : Interface10
data class Impl2Interface10(val x: String) : Interface10
data class Impl3Interface10(val x: String) : Interface10
data class Impl4Interface10(val x: String) : Interface10
data class Impl5Interface10(val x: String) : Interface10
data class Impl6Interface10(val x: String) : Interface10
data class Impl7Interface10(val x: String) : Interface10
data class Impl8Interface10(val x: String) : Interface10
data class Impl9Interface10(val x: String) : Interface10
data class Impl10Interface10(val x: String) : Interface10
data class Impl11Interface10(val x: String) : Interface10
data class Impl12Interface10(val x: String) : Interface10
data class Impl13Interface10(val x: String) : Interface10
data class Impl14Interface10(val x: String) : Interface10
data class Impl15Interface10(val x: String) : Interface10
data class Impl16Interface10(val x: String) : Interface10
data class Impl17Interface10(val x: String) : Interface10
data class Impl18Interface10(val x: String) : Interface10
data class Impl19Interface10(val x: String) : Interface10


interface Interface11
data class Impl1Interface11(val x: String) : Interface11
data class Impl2Interface11(val x: String) : Interface11
data class Impl3Interface11(val x: String) : Interface11
data class Impl4Interface11(val x: String) : Interface11
data class Impl5Interface11(val x: String) : Interface11
data class Impl6Interface11(val x: String) : Interface11
data class Impl7Interface11(val x: String) : Interface11
data class Impl8Interface11(val x: String) : Interface11
data class Impl9Interface11(val x: String) : Interface11
data class Impl10Interface11(val x: String) : Interface11
data class Impl11Interface11(val x: String) : Interface11
data class Impl12Interface11(val x: String) : Interface11
data class Impl13Interface11(val x: String) : Interface11
data class Impl14Interface11(val x: String) : Interface11
data class Impl15Interface11(val x: String) : Interface11
data class Impl16Interface11(val x: String) : Interface11
data class Impl17Interface11(val x: String) : Interface11
data class Impl18Interface11(val x: String) : Interface11
data class Impl19Interface11(val x: String) : Interface11

interface Interface12
data class Impl1Interface12(val x: String) : Interface12
data class Impl2Interface12(val x: String) : Interface12
data class Impl3Interface12(val x: String) : Interface12
data class Impl4Interface12(val x: String) : Interface12
data class Impl5Interface12(val x: String) : Interface12
data class Impl6Interface12(val x: String) : Interface12
data class Impl7Interface12(val x: String) : Interface12
data class Impl8Interface12(val x: String) : Interface12
data class Impl9Interface12(val x: String) : Interface12
data class Impl10Interface12(val x: String) : Interface12
data class Impl11Interface12(val x: String) : Interface12
data class Impl12Interface12(val x: String) : Interface12
data class Impl13Interface12(val x: String) : Interface12
data class Impl14Interface12(val x: String) : Interface12
data class Impl15Interface12(val x: String) : Interface12
data class Impl16Interface12(val x: String) : Interface12
data class Impl17Interface12(val x: String) : Interface12
data class Impl18Interface12(val x: String) : Interface12
data class Impl19Interface12(val x: String) : Interface12

interface Interface13
data class Impl1Interface13(val x: String) : Interface13
data class Impl2Interface13(val x: String) : Interface13
data class Impl3Interface13(val x: String) : Interface13
data class Impl4Interface13(val x: String) : Interface13
data class Impl5Interface13(val x: String) : Interface13
data class Impl6Interface13(val x: String) : Interface13
data class Impl7Interface13(val x: String) : Interface13
data class Impl8Interface13(val x: String) : Interface13
data class Impl9Interface13(val x: String) : Interface13
data class Impl10Interface13(val x: String) : Interface13
data class Impl11Interface13(val x: String) : Interface13
data class Impl12Interface13(val x: String) : Interface13
data class Impl13Interface13(val x: String) : Interface13
data class Impl14Interface13(val x: String) : Interface13
data class Impl15Interface13(val x: String) : Interface13
data class Impl16Interface13(val x: String) : Interface13
data class Impl17Interface13(val x: String) : Interface13
data class Impl18Interface13(val x: String) : Interface13
data class Impl19Interface13(val x: String) : Interface13

interface Interface14
data class Impl1Interface14(val x: String) : Interface14
data class Impl2Interface14(val x: String) : Interface14
data class Impl3Interface14(val x: String) : Interface14
data class Impl4Interface14(val x: String) : Interface14
data class Impl5Interface14(val x: String) : Interface14
data class Impl6Interface14(val x: String) : Interface14
data class Impl7Interface14(val x: String) : Interface14
data class Impl8Interface14(val x: String) : Interface14
data class Impl9Interface14(val x: String) : Interface14
data class Impl10Interface14(val x: String) : Interface14
data class Impl11Interface14(val x: String) : Interface14
data class Impl12Interface14(val x: String) : Interface14
data class Impl13Interface14(val x: String) : Interface14
data class Impl14Interface14(val x: String) : Interface14
data class Impl15Interface14(val x: String) : Interface14
data class Impl16Interface14(val x: String) : Interface14
data class Impl17Interface14(val x: String) : Interface14
data class Impl18Interface14(val x: String) : Interface14
data class Impl19Interface14(val x: String) : Interface14

interface Interface15
data class Impl1Interface15(val x: String) : Interface15
data class Impl2Interface15(val x: String) : Interface15
data class Impl3Interface15(val x: String) : Interface15
data class Impl4Interface15(val x: String) : Interface15
data class Impl5Interface15(val x: String) : Interface15
data class Impl6Interface15(val x: String) : Interface15
data class Impl7Interface15(val x: String) : Interface15
data class Impl8Interface15(val x: String) : Interface15
data class Impl9Interface15(val x: String) : Interface15
data class Impl10Interface15(val x: String) : Interface15
data class Impl11Interface15(val x: String) : Interface15
data class Impl12Interface15(val x: String) : Interface15
data class Impl13Interface15(val x: String) : Interface15
data class Impl14Interface15(val x: String) : Interface15
data class Impl15Interface15(val x: String) : Interface15
data class Impl16Interface15(val x: String) : Interface15
data class Impl17Interface15(val x: String) : Interface15
data class Impl18Interface15(val x: String) : Interface15
data class Impl19Interface15(val x: String) : Interface15


data class Fooed(
        val val1: List<Interface1>,
        val val2: List<Interface2>,
        val val3: List<Interface3>,
        val val4: List<Interface4>,
        val val5: List<Interface5>,
        val val6: List<Interface6>,
        val val7: List<Interface7>,
        val val8: List<Interface8>,
        val val9: List<Interface9>,
        val val10: List<Interface10>,
        val val11: List<Interface11>,
        val val12: List<Interface12>,
        val val13: List<Interface13>,
        val val14: List<Interface14>,
        val val15: List<Interface15>,
        val sealed: List<SealedClass>
)

interface AnInterface
object AnObject
data class AClassWithObject(val anObject: AnObject)
data class AnImplementation(val x: String): AnInterface
data class AClassWithInterface(val inter: AnInterface, val foo: Interface1)
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

