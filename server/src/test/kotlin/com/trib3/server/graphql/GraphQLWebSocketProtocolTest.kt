package com.trib3.server.graphql

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.fasterxml.jackson.module.kotlin.readValue
import com.trib3.json.ObjectMapperProvider
import org.testng.annotations.Test

class GraphQLWebSocketProtocolTest {
    val mapper = ObjectMapperProvider().get()
    @Test
    fun testOperationTypeRawDeserialization() {
        val start = OperationType.getOperationType("start")
        assertThat(start).isEqualTo(OperationType.GQL_START)
        assertThat(start?.payloadType).isEqualTo(GraphRequest::class)
    }

    @Test
    fun testOperationTypeJsonDeserialization() {
        val start = mapper.readValue<OperationType<*>>("\"start\"")
        assertThat(start).isEqualTo(OperationType.GQL_START)
    }

    @Test
    fun testOperationTypeJsonSerialization() {
        val stringified = mapper.writeValueAsString(OperationType.GQL_START)
        assertThat(stringified).isEqualTo("\"start\"")
    }

    @Test
    fun testOperationMessageJsonDeserialization() {
        val start = mapper.readValue<OperationMessage<GraphRequest>>(
            """
            {
                "type": "start",
                "id": "123",
                "payload": {
                    "query": "hi",
                    "variables": {},
                    "operationName": "boo"
                }
            }
        """.trimIndent()
        )
        assertThat(start.type).isEqualTo(OperationType.GQL_START)
        assertThat(start.id).isEqualTo("123")
        assertThat(start.payload).isNotNull().isInstanceOf(GraphRequest::class)
        assertThat((start.payload as GraphRequest).query).isEqualTo("hi")
        assertThat((start.payload as GraphRequest).variables).isNotNull().isInstanceOf(Map::class)
            .isEqualTo(mapOf<String, Any?>())
        assertThat((start.payload as GraphRequest).operationName).isEqualTo("boo")
    }
}
