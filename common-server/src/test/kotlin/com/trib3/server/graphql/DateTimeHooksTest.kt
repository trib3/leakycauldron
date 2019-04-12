package com.trib3.server.graphql

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.expedia.graphql.SchemaGeneratorConfig
import com.expedia.graphql.TopLevelObject
import com.expedia.graphql.toSchema
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.CoercingSerializeException
import org.testng.annotations.Test
import org.threeten.extra.YearQuarter
import java.time.YearMonth

class DateTimeQuery {
    fun quarter(q: YearQuarter): YearQuarter {
        return q.plusQuarters(1)
    }

    fun month(m: YearMonth): YearMonth {
        return m.plusMonths(1)
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
        }.thrownError {
            isInstanceOf(CoercingSerializeException::class)
        }
        assertThat {
            graphQL.execute("""query {quarter(q:"123")}""")
        }.thrownError {
            isInstanceOf(CoercingSerializeException::class)
        }
        assertThat {
            YEAR_QUARTER_SCALAR.coercing.serialize(123)
        }.thrownError {
            isInstanceOf(CoercingSerializeException::class)
        }
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
        }.thrownError {
            isInstanceOf(CoercingSerializeException::class)
        }
        assertThat {
            graphQL.execute("""query {month(m:"123")}""")
        }.thrownError {
            isInstanceOf(CoercingSerializeException::class)
        }
        assertThat {
            YEAR_MONTH_SCALAR.coercing.serialize(123)
        }.thrownError {
            isInstanceOf(CoercingSerializeException::class)
        }
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
}
