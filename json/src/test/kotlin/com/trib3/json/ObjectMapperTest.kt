package com.trib3.json

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.message
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.inject.multibindings.MapBinder
import com.google.inject.name.Names
import com.trib3.json.modules.ObjectMapperModule
import dev.misfitlabs.kotlinguice4.KotlinModule
import dev.misfitlabs.kotlinguice4.typeLiteral
import org.testng.annotations.Guice
import org.testng.annotations.Test
import org.threeten.extra.YearQuarter
import java.time.LocalDate
import javax.inject.Inject
import kotlin.reflect.KClass

private data class SimpleBean(
    val foo: String,
    val bar: Int,
    val maybe: String?,
    val date: LocalDate?,
    val ignoreMe: String? = null
)

private abstract class SimpleMixin(
    @JsonIgnore
    val ignoreMe: String? = null
)

private class SimpleMixinModule : KotlinModule() {
    override fun configure() {
        MapBinder.newMapBinder(
            binder(),
            typeLiteral<KClass<*>>(),
            typeLiteral<KClass<*>>(),
            Names.named(ObjectMapperProvider.OBJECT_MAPPER_MIXINS)
        ).addBinding(SimpleBean::class).toInstance(SimpleMixin::class)
    }
}

@Guice(modules = [ObjectMapperModule::class, SimpleMixinModule::class])
class ObjectMapperTest
@Inject constructor(val mapper: ObjectMapper) {

    @Test
    fun testMapper() {
        val bean = SimpleBean("hahaha", 3, "yes", LocalDate.of(2019, 1, 1))
        val stringRep = mapper.writeValueAsString(bean)
        assertThat(stringRep).contains("\"date\":\"2019-01-01\"")
        assertThat(stringRep).doesNotContain("ignoreMe")
        assertThat(ObjectMapperProvider().get().writeValueAsString(bean)).contains("ignoreMe")
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

    @Test
    fun testYearQuarter() {
        val yq = YearQuarter.of(2010, 1)
        assertThat(mapper.writeValueAsString(yq)).isEqualTo("\"2010-Q1\"")
        assertThat(mapper.readValue<YearQuarter>("\"2010-Q1\"")).isEqualTo(yq)
        assertThat(mapper.readValue<YearQuarter>("\"\"")).isNull()
        assertThat {
            mapper.readValue<YearQuarter>("123")
        }.isFailure().all {
            isInstanceOf(JsonMappingException::class)
            message().isNotNull().contains("Expected VALUE_STRING for YearQuarter but saw")
        }
    }

    private data class YQContainer(val yearQuarter: YearQuarter)

    @Test
    fun testYearQuarterContainer() {
        val bean = YQContainer(YearQuarter.of(2010, 1))
        assertThat(mapper.writeValueAsString(bean)).isEqualTo("{\"yearQuarter\":\"2010-Q1\"}")
        assertThat(mapper.readValue<YQContainer>("{\"yearQuarter\":\"2010-Q1\"}")).isEqualTo(bean)
    }

    @Test
    fun testYearQuarterMap() {
        val map = mapOf(YearQuarter.of(2010, 1) to YearQuarter.of(2011, 2))
        assertThat(mapper.writeValueAsString(map)).isEqualTo("{\"2010-Q1\":\"2011-Q2\"}")
        assertThat(mapper.readValue<Map<YearQuarter, YearQuarter>>("{\"2010-Q1\":\"2011-Q2\"}")).isEqualTo(map)
        assertThat {
            mapper.readValue<Map<YearQuarter, YearQuarter>>("{\"abc\": \"2011-Q2\"}")
        }.isFailure().all {
            isInstanceOf(JsonMappingException::class)
            message().isNotNull().contains("Unexpected quarter")
        }
    }
}
