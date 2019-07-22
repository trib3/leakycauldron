package com.trib3.server.graphql

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isSuccess
import com.expedia.graphql.SchemaGeneratorConfig
import com.expedia.graphql.TopLevelObject
import com.expedia.graphql.toSchema
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.CoercingSerializeException
import org.testng.annotations.Test
import org.threeten.extra.YearQuarter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class DateTimeQuery {
    fun quarter(q: YearQuarter): YearQuarter {
        return q.plusQuarters(1)
    }

    fun month(m: YearMonth): YearMonth {
        return m.plusMonths(1)
    }

    fun localDateTime(l: LocalDateTime): LocalDateTime {
        return l.plusDays(1).plusHours(1)
    }

    fun localDate(l: LocalDate): LocalDate {
        return l.plusDays(1)
    }

    fun localTime(l: LocalTime): LocalTime {
        return l.plusHours(1)
    }

    fun offsetDateTime(o: OffsetDateTime): OffsetDateTime {
        return o.plusDays(1)
    }
}

class DateTimeHooksTest {
    val config =
        SchemaGeneratorConfig(listOf(), hooks = DateTimeHooks())
    val graphQL =
        GraphQL.newGraphQL(
            toSchema(config, listOf(TopLevelObject(DateTimeQuery())))
        ).build()

    @Test
    fun testQuarter() {
        val result = graphQL.execute("""query {quarter(q:"2019-Q1")}""").getData<Map<String, String>>()
        assertThat(result["quarter"]).isEqualTo("2019-Q2")
        assertThat {
            graphQL.execute("""query {quarter(q:123)}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)
        assertThat {
            graphQL.execute("""query {quarter(q:"123")}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            YEAR_QUARTER_SCALAR.coercing.serialize(123)
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            YEAR_QUARTER_SCALAR.coercing.serialize(YearQuarter.of(2019, 2))
        }.isSuccess().isEqualTo("2019-Q2")
    }

    @Test
    fun testQuarterVariable() {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: Quarter!) {quarter(q:${'$'}input)}""")
                .variables(mapOf("input" to "2019-Q1")).build()
        ).getData<Map<String, String>>()
        assertThat(result["quarter"]).isEqualTo("2019-Q2")
    }

    @Test
    fun testMonth() {
        val result = graphQL.execute("""query {month(m:"2019-01")}""").getData<Map<String, String>>()
        assertThat(result["month"]).isEqualTo("2019-02")
        assertThat {
            graphQL.execute("""query {month(m:123)}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            graphQL.execute("""query {month(m:"123")}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            YEAR_MONTH_SCALAR.coercing.serialize(123)
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            YEAR_MONTH_SCALAR.coercing.serialize(YearMonth.of(2019, 10))
        }.isSuccess().isEqualTo("2019-10")
    }

    @Test
    fun testMonthVariable() {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: Month!) {month(m:${'$'}input)}""")
                .variables(mapOf("input" to "2019-01")).build()
        ).getData<Map<String, String>>()
        assertThat(result["month"]).isEqualTo("2019-02")
    }

    @Test
    fun testLocalDateTime() {
        val result = graphQL.execute("""query {localDateTime(l:"2019-10-30T00:01")}""")
            .getData<Map<String, String>>()
        assertThat(result["localDateTime"]).isEqualTo("2019-10-31T01:01")
        assertThat {
            graphQL.execute("""query {localDateTime(l:123)}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            graphQL.execute("""query {localDateTime(l:"123")}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            LOCAL_DATETIME_SCALAR.coercing.serialize(123)
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            LOCAL_DATETIME_SCALAR.coercing.serialize(LocalDateTime.of(2019, 10, 31, 1, 1))
        }.isSuccess().isEqualTo("2019-10-31T01:01")
    }

    @Test
    fun testLocalDateTimeVariable() {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: LocalDateTime!) {localDateTime(l:${'$'}input)}""")
                .variables(mapOf("input" to "2019-10-30T00:01")).build()
        ).getData<Map<String, String>>()
        assertThat(result["localDateTime"]).isEqualTo("2019-10-31T01:01")
    }

    @Test
    fun testLocalDate() {
        val result = graphQL.execute("""query {localDate(l:"2019-10-30")}""")
            .getData<Map<String, String>>()
        assertThat(result["localDate"]).isEqualTo("2019-10-31")
        assertThat {
            graphQL.execute("""query {localDate(l:123)}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            graphQL.execute("""query {localDate(l:"123")}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            LOCAL_DATE_SCALAR.coercing.serialize(123)
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            LOCAL_DATE_SCALAR.coercing.serialize(LocalDate.of(2019, 10, 31))
        }.isSuccess().isEqualTo("2019-10-31")
    }

    @Test
    fun testLocalDateVariable() {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: LocalDate!) {localDate(l:${'$'}input)}""")
                .variables(mapOf("input" to "2019-10-30")).build()
        ).getData<Map<String, String>>()
        assertThat(result["localDate"]).isEqualTo("2019-10-31")
    }

    @Test
    fun testLocalTime() {
        val result = graphQL.execute("""query {localTime(l:"00:01")}""")
            .getData<Map<String, String>>()
        assertThat(result["localTime"]).isEqualTo("01:01")
        assertThat {
            graphQL.execute("""query {localTime(l:123)}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            graphQL.execute("""query {localTime(l:"123")}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            LOCAL_TIME_SCALAR.coercing.serialize(123)
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            LOCAL_TIME_SCALAR.coercing.serialize(LocalTime.of(1, 1))
        }.isSuccess().isEqualTo("01:01")
    }

    @Test
    fun testLocalTimeVariable() {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: LocalTime!) {localTime(l:${'$'}input)}""")
                .variables(mapOf("input" to "00:01")).build()
        ).getData<Map<String, String>>()
        assertThat(result["localTime"]).isEqualTo("01:01")
    }

    @Test
    fun testOffsetDateTime() {
        val result = graphQL.execute("""query {offsetDateTime(o:"2019-10-30T00:01-07:00")}""")
            .getData<Map<String, String>>()
        assertThat(result["offsetDateTime"]).isEqualTo("2019-10-31T00:01-07:00")
        assertThat {
            graphQL.execute("""query {offsetDateTime(o:123)}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            graphQL.execute("""query {offsetDateTime(o:"123")}""")
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            OFFSET_DATETIME_SCALAR.coercing.serialize(123)
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            OFFSET_DATETIME_SCALAR.coercing.serialize(
                OffsetDateTime.of(2019, 10, 31, 1, 1, 31, 129, ZoneOffset.ofHours(-7))
            )
        }.isSuccess().isEqualTo("2019-10-31T01:01:31.000000129-07:00")
    }

    @Test
    fun testOffsetDateTimeVariable() {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: OffsetDateTime!) {offsetDateTime(o:${'$'}input)}""")
                .variables(mapOf("input" to "2019-10-30T00:01-07:00")).build()
        ).getData<Map<String, String>>()
        assertThat(result["offsetDateTime"]).isEqualTo("2019-10-31T00:01-07:00")
    }
}
