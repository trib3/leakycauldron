package com.trib3.json

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.trib3.json.modules.ObjectMapperModule
import org.testng.annotations.Guice
import org.testng.annotations.Test
import java.time.LocalDate
import javax.inject.Inject

private data class SimpleBean(val foo: String, val bar: Int, val maybe: String?, val date: LocalDate?)

@Guice(modules = [ObjectMapperModule::class])
class ObjectMapperTest
@Inject constructor(val mapper: ObjectMapper) {

    @Test
    fun testMapper() {
        val bean = SimpleBean("hahaha", 3, "yes", LocalDate.of(2019, 1, 1))
        val stringRep = mapper.writeValueAsString(bean)
        assertThat(stringRep.contains("\"date\": \"2019-01-01\""))
        val roundTrip = mapper.readValue<SimpleBean>(stringRep)
        assertThat(bean).isEqualTo(roundTrip)
    }

    @Test
    fun testPermissive() {
        val bean = mapper.readValue<SimpleBean>("{\"foo\": \"haha\", \"bar\": 3, \"baz\": \"extra\"}")
        assertThat(bean.foo).isEqualTo("haha")
        assertThat(bean.bar).isEqualTo(3)
        assertThat(bean.maybe).isNull()
        assertThat(bean.date).isNull()
    }

    @Test
    fun testModuleEq() {
        assertThat(ObjectMapperModule()).isEqualTo(ObjectMapperModule())
    }
}
