package com.trib3.graphql.execution

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isSuccess
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.execution.SimpleKotlinDataFetcherFactoryProvider
import com.expediagroup.graphql.generator.toSchema
import com.trib3.json.ObjectMapperProvider
import graphql.ErrorType
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.CoercingSerializeException
import org.testng.annotations.Test
import org.threeten.extra.YearQuarter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Year
import java.time.YearMonth
import java.util.UUID

class HooksQuery {
    fun year(y: Year): Year {
        return y.plusYears(1)
    }

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

    fun newuuid(): UUID {
        return UUID.randomUUID()
    }

    fun existinguuid(uuid: UUID): String {
        return uuid.toString()
    }

    fun bigDecimal(): BigDecimal {
        return BigDecimal.TEN
    }
}

class LeakyCauldronHooksTest {
    val hooks = LeakyCauldronHooks()
    val config =
        SchemaGeneratorConfig(
            listOf(this::class.java.packageName),
            hooks = hooks,
            dataFetcherFactoryProvider = SimpleKotlinDataFetcherFactoryProvider(
                ObjectMapperProvider().get()
            )
        )
    val graphQL =
        GraphQL.newGraphQL(
            toSchema(config, listOf(TopLevelObject(HooksQuery())))
        ).build()

    /**
     * Assert that executing each of the [invalidQueries] results in a validation error
     */
    private fun assertValidationErrors(vararg invalidQueries: String) {
        for (query in invalidQueries) {
            val result = graphQL.execute(query)
            assertThat(result.errors[0].errorType).isEqualTo(ErrorType.ValidationError)
        }
    }

    @Test
    fun testYear() {
        val result = graphQL.execute("""query {year(y:"2019")}""").getData<Map<String, String>>()
        assertThat(result["year"]).isEqualTo("2020")
        assertValidationErrors("""query {year(y:123)}""", """query {year(y:"123-45")}""")

        assertThat {
            YEAR_SCALAR.coercing.serialize(123)
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            YEAR_SCALAR.coercing.serialize(Year.of(2019))
        }.isSuccess().isEqualTo("2019")
    }

    @Test
    fun testYearVariable() {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: Year!) {year(y:${'$'}input)}""")
                .variables(mapOf("input" to "2019")).build()
        ).getData<Map<String, String>>()
        assertThat(result["year"]).isEqualTo("2020")
    }

    @Test
    fun testQuarter() {
        val result = graphQL.execute("""query {quarter(q:"2019-Q1")}""").getData<Map<String, String>>()
        assertThat(result["quarter"]).isEqualTo("2019-Q2")
        assertValidationErrors("""query {quarter(q:123)}""", """query {quarter(q:"123")}""")

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
        assertValidationErrors("""query {month(m:123)}""", """query {month(m:"123")}""")

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
        assertThat(result["localDateTime"]).isEqualTo("2019-10-31T01:01:00.000")
        assertValidationErrors("""query {localDateTime(l:123)}""", """query {localDateTime(l:"123")}""")

        assertThat {
            LOCAL_DATETIME_SCALAR.coercing.serialize(123)
        }.isFailure().isInstanceOf(CoercingSerializeException::class)

        assertThat {
            LOCAL_DATETIME_SCALAR.coercing.serialize(LocalDateTime.of(2019, 10, 31, 1, 1))
        }.isSuccess().isEqualTo("2019-10-31T01:01:00.000")
    }

    @Test
    fun testLocalDateTimeVariable() {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: LocalDateTime!) {localDateTime(l:${'$'}input)}""")
                .variables(mapOf("input" to "2019-10-30T00:01")).build()
        ).getData<Map<String, String>>()
        assertThat(result["localDateTime"]).isEqualTo("2019-10-31T01:01:00.000")
    }

    @Test
    fun testLocalDate() {
        val result = graphQL.execute("""query {localDate(l:"2019-10-30")}""")
            .getData<Map<String, String>>()
        assertThat(result["localDate"]).isEqualTo("2019-10-31")
        assertValidationErrors("""query {localDate(l:123)}""", """query {localDate(l:"123")}""")
    }

    @Test
    fun testLocalDateVariable() {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: Date!) {localDate(l:${'$'}input)}""")
                .variables(mapOf("input" to "2019-10-30")).build()
        ).getData<Map<String, String>>()
        assertThat(result["localDate"]).isEqualTo("2019-10-31")
    }

    @Test
    fun testLocalTime() {
        val result = graphQL.execute("""query {localTime(l:"00:01")}""")
            .getData<Map<String, String>>()
        assertThat(result["localTime"]).isEqualTo("01:01:00")
        assertValidationErrors("""query {localTime(l:123)}""", """query {localTime(l:"123")}""")
    }

    @Test
    fun testLocalTimeVariable() {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: LocalTime!) {localTime(l:${'$'}input)}""")
                .variables(mapOf("input" to "00:01")).build()
        ).getData<Map<String, String>>()
        assertThat(result["localTime"]).isEqualTo("01:01:00")
    }

    @Test
    fun testOffsetDateTime() {
        val result = graphQL.execute("""query {offsetDateTime(o:"2019-10-30T00:01-07:00")}""")
            .getData<Map<String, String>>()
        assertThat(result["offsetDateTime"]).isEqualTo("2019-10-31T07:01:00.000Z")
        assertValidationErrors("""query {offsetDateTime(o:123)}""", """query {offsetDateTime(o:"123")}""")
    }

    @Test
    fun testOffsetDateTimeVariable() {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: DateTime!) {offsetDateTime(o:${'$'}input)}""")
                .variables(mapOf("input" to "2019-10-30T00:01-07:00")).build()
        ).getData<Map<String, String>>()
        assertThat(result["offsetDateTime"]).isEqualTo("2019-10-31T07:01:00.000Z")
    }

    @Test
    fun testUUIDGeneration() {
        val result = graphQL.execute("""query { newuuid }""")
            .getData<Map<String, String>>()
        assertThat {
            UUID.fromString(result["newuuid"])
        }.isSuccess()
    }

    @Test
    fun testUUIDInput() {
        val uuid = UUID.randomUUID()
        val result = graphQL.execute("""query { existinguuid(uuid:"$uuid") }""")
            .getData<Map<String, String>>()
        assertThat(result["existinguuid"]).isEqualTo(uuid.toString())
        assertValidationErrors("""query {existinguuid(uuid:123)}""", """query {existinguuid(uuid:"123")}""")
    }

    @Test
    fun testUUIDVariable() {
        val uuid = UUID.randomUUID()
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query("""query(${'$'}input: UUID!) { existinguuid(uuid:${'$'}input) }""")
                .variables(mapOf("input" to uuid.toString())).build()
        ).getData<Map<String, String>>()
        assertThat(result["existinguuid"]).isEqualTo(uuid.toString())
    }

    @Test
    fun testBigDecimal() {
        val result = graphQL.execute("""query { bigDecimal }""").getData<Map<String, BigDecimal>>()
        assertThat(result["bigDecimal"]).isEqualTo(BigDecimal.TEN)
    }
}
