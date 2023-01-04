package com.trib3.testing

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSuccess
import assertk.assertions.message
import org.easymock.EasyMock
import org.easymock.EasyMockSupport
import org.testng.annotations.Test

interface Thing

interface TestClass {
    fun manipulateString(str: String): String
    fun manipulateThing(thing: Thing): Thing
    fun processThing(thing: Thing)
    fun getThing(): Thing
}

open class RealThing(val instance: Int) : Thing {
    override fun equals(other: Any?): Boolean {
        if (other is RealThing) {
            return instance == other.instance
        }
        return false
    }

    override fun hashCode(): Int {
        return instance.hashCode()
    }
}

class LeakyMockTest {
    @Test
    fun testMockSupportAndStringMatchers() {
        val support = EasyMockSupport()
        val mock = support.mock<TestClass>()
        EasyMock.expect(mock.manipulateString(LeakyMock.anyString())).andReturn("bah!").once()
        EasyMock.expect(mock.manipulateString(LeakyMock.contains("foo"))).andReturn("bar!").once()
        EasyMock.expect(
            mock.manipulateString(
                LeakyMock.and(
                    LeakyMock.contains("foo"),
                    LeakyMock.contains("bar"),
                ),
            ),
        ).andReturn("baz!").once()
        EasyMock.expect(
            mock.manipulateString(
                LeakyMock.and(
                    LeakyMock.contains("foo"),
                    LeakyMock.contains("bar"),
                    LeakyMock.contains("baz"),
                ),
            ),
        ).andReturn("bazh!").once()
        EasyMock.expect(
            mock.manipulateString(
                LeakyMock.or(
                    LeakyMock.contains("foo"),
                    LeakyMock.contains("bar"),
                ),
            ),
        ).andReturn("bash!").times(2)
        EasyMock.expect(
            mock.manipulateString(
                LeakyMock.or(
                    LeakyMock.contains("foo"),
                    LeakyMock.contains("bar"),
                    LeakyMock.contains("baz"),
                ),
            ),
        ).andReturn("bazh!!!").times(3)
        EasyMock.expect(mock.manipulateString(LeakyMock.not(LeakyMock.contains("foo")))).andReturn("bar!").once()
        EasyMock.expect(mock.manipulateString(LeakyMock.find("\\d+"))).andReturn("nums!").once()
        EasyMock.expect(mock.manipulateString(LeakyMock.matches("\\d+"))).andReturn("matchnums!").once()
        EasyMock.expect(mock.manipulateString(LeakyMock.startsWtih("foo"))).andReturn("starts!").once()
        EasyMock.expect(mock.manipulateString(LeakyMock.endsWith("foo"))).andReturn("ends!").once()
        support.replayAll()
        assertThat(mock.manipulateString("blee")).isEqualTo("bah!")
        assertThat(mock.manipulateString("lalafoolala")).isEqualTo("bar!")
        assertThat(mock.manipulateString("lalabarlalafoolala")).isEqualTo("baz!")
        assertThat(mock.manipulateString("lbazalabarlalafoolala")).isEqualTo("bazh!")
        assertThat(mock.manipulateString("lalafoolala")).isEqualTo("bash!")
        assertThat(mock.manipulateString("lalabarlala")).isEqualTo("bash!")
        assertThat(mock.manipulateString("lalafoolala")).isEqualTo("bazh!!!")
        assertThat(mock.manipulateString("lalabarlala")).isEqualTo("bazh!!!")
        assertThat(mock.manipulateString("lalabazlala")).isEqualTo("bazh!!!")
        assertThat(mock.manipulateString("not_any_f_O_o")).isEqualTo("bar!")
        assertThat(mock.manipulateString("numbers 123 are here")).isEqualTo("nums!")
        assertThat(mock.manipulateString("123")).isEqualTo("matchnums!")
        assertThat(mock.manipulateString("foolala")).isEqualTo("starts!")
        assertThat(mock.manipulateString("lalalafoo")).isEqualTo("ends!")
        support.verifyAll()
    }

    @Test
    fun testMockAndObjectMatchers() {
        val mock = LeakyMock.mock(TestClass::class.java)
        val mockedThing = LeakyMock.mock<Thing>()
        EasyMock.expect(mock.getThing()).andReturn(RealThing(1)).once()
        EasyMock.expect(mock.manipulateThing(LeakyMock.anyObject(Thing::class.java))).andReturn(mockedThing).once()
        EasyMock.expect(mock.processThing(LeakyMock.anyObject())).once()
        EasyMock.expect(mock.processThing(LeakyMock.isA<RealThing>())).once()
        EasyMock.expect(mock.processThing(LeakyMock.isA(RealThing::class.java))).once()
        EasyMock.replay(mock, mockedThing)
        assertThat(mock.getThing()).isEqualTo(RealThing(1))
        assertThat(mock.manipulateThing(mockedThing)).isEqualTo(mockedThing)
        assertThat { mock.processThing(mockedThing) }.isSuccess()
        assertThat { mock.processThing(RealThing(2)) }.isSuccess()
        assertThat { mock.processThing(RealThing(3)) }.isSuccess()
        EasyMock.verify(mock, mockedThing)
    }

    @Test
    fun testNiceMocks() {
        val niceMock = LeakyMock.niceMock<TestClass>()
        val otherNiceMock = LeakyMock.niceMock(TestClass::class.java)
        EasyMock.expect(niceMock.getThing()).andReturn(RealThing(1)).once()
        EasyMock.expect(otherNiceMock.getThing()).andReturn(RealThing(2)).once()
        EasyMock.replay(niceMock, otherNiceMock)
        assertThat(niceMock.getThing()).isEqualTo(RealThing(1))
        assertThat(otherNiceMock.getThing()).isEqualTo(RealThing(2))
        assertThat(niceMock.getThing()).isNull()
        assertThat(otherNiceMock.getThing()).isNull()
        EasyMock.verify(niceMock, otherNiceMock)
    }

    @Test
    fun testCapture() {
        val capture = EasyMock.newCapture<Thing>()
        val mock = LeakyMock.mock("namedMock", TestClass::class.java)
        EasyMock.expect(mock.manipulateThing(LeakyMock.capture(capture))).andReturn(RealThing(1))
        EasyMock.replay(mock)
        assertThat(mock.manipulateThing(RealThing(2))).isEqualTo(RealThing(1))
        assertThat(capture.value).isEqualTo(RealThing(2))
        EasyMock.verify(mock)
    }

    @Test
    fun showNullProblems() {
        val mock = LeakyMock.niceMock("namedNiceMock", TestClass::class.java)
        assertThat {
            EasyMock.expect(mock.manipulateThing(EasyMock.anyObject()))
        }.isFailure().message().isNotNull().contains("anyObject() must not be null")
        assertThat {
            EasyMock.expect(mock.manipulateString(EasyMock.anyString()))
        }.isFailure().message().isNotNull().contains("anyString() must not be null")
    }

    @Test
    fun testInvalidAndOr() {
        assertThat {
            LeakyMock.and<Boolean>()
        }.isFailure().message().isNotNull().contains("at least one argument")
        assertThat {
            LeakyMock.or<Boolean>()
        }.isFailure().message().isNotNull().contains("at least one argument")
    }
}
