package com.trib3.server.graphql

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.expedia.graphql.SchemaGeneratorConfig
import com.expedia.graphql.TopLevelObject
import com.expedia.graphql.toSchema
import graphql.GraphQL
import org.slf4j.MDC
import org.testng.annotations.Test

class Query

class RequestIdInstrumentationTest {
    val config =
        SchemaGeneratorConfig(listOf())
    val graphQL =
        GraphQL.newGraphQL(
            toSchema(
                config, listOf(TopLevelObject(Query()))
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
