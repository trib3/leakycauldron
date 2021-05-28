package com.trib3.graphql.execution

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.toSchema
import graphql.GraphQL
import org.slf4j.MDC
import org.testng.annotations.Test

class Query {
    fun test(): String {
        return "test"
    }
}

class RequestIdInstrumentationTest {
    val config =
        SchemaGeneratorConfig(listOf(this::class.java.packageName))
    val graphQL =
        GraphQL.newGraphQL(
            toSchema(
                config,
                listOf(TopLevelObject(Query()))
            )
        ).instrumentation(RequestIdInstrumentation()).build()

    @Test
    fun testInstrumentation() {
        val prevMDC = MDC.get("RequestId")
        MDC.put("RequestId", "RequestIdInstrumentationTest::testInstrumentation")
        try {
            val result = graphQL.execute("query {}")
            assertThat(result.extensions["RequestId"])
                .isEqualTo("RequestIdInstrumentationTest::testInstrumentation")
        } finally {
            if (prevMDC == null) {
                MDC.clear()
            } else {
                MDC.put("RequestId", prevMDC)
            }
        }
    }
}
