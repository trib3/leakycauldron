package com.trib3.graphql.websocket

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLResponse
import com.fasterxml.jackson.module.kotlin.readValue
import com.trib3.graphql.execution.MessageGraphQLError
import com.trib3.json.ObjectMapperProvider
import org.testng.annotations.Test
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

class GraphQLWebSocketProtocolTest {
    val mapper = ObjectMapperProvider().get()

    @Test
    fun testOperationTypeRawDeserialization() {
        val start = OperationType.getOperationType("start")
        assertThat(start).isEqualTo(OperationType.GQL_START)
        assertThat(start?.payloadType).isEqualTo(GraphQLRequest::class)
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
        val start = mapper.readValue<OperationMessage<*>>(
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
        assertThat(start.payload).isNotNull().isInstanceOf(GraphQLRequest::class)
        assertThat((start.payload as GraphQLRequest).query).isEqualTo("hi")
        assertThat((start.payload as GraphQLRequest).variables).isNotNull().isInstanceOf(Map::class)
            .isEqualTo(mapOf<String, Any?>())
        assertThat((start.payload as GraphQLRequest).operationName).isEqualTo("boo")
    }

    @Test
    fun testJsonRoundTrips() {
        val objectExample = mapOf(
            Nothing::class to null,
            String::class to "message",
            GraphQLRequest::class to GraphQLRequest("query {q}"),
            Map::class to mapOf("a" to "b", "c" to "d"),
            List::class to listOf(
                MessageGraphQLError("e").toSpecification(),
                MessageGraphQLError("f").toSpecification()
            ),
            GraphQLResponse::class to null // only need to support serialization right now, not round trip
        )
        for (
            t in OperationType.Companion::class.memberProperties
                .filterIsInstance<KProperty1<OperationType.Companion, OperationType<Any>>>()
        ) {
            val type = t.get(OperationType.Companion)
            val message =
                OperationMessage(
                    type,
                    "${type.type}_id",
                    objectExample.getValue(type.payloadType)
                )
            val serialized = mapper.writeValueAsString(message)
            val deserialized = mapper.readValue<OperationMessage<*>>(serialized)
            assertThat(deserialized).isEqualTo(message)
        }
    }

    @Test
    fun testNoPayloadMessage() {
        val json =
            """{"type": "connection_init", "id": "123"}"""
        val message = mapper.readValue<OperationMessage<*>>(json)
        assertThat(message.type).isEqualTo(OperationType.GQL_CONNECTION_INIT)
        assertThat(message.id).isEqualTo("123")
        assertThat(message.payload).isNull()
    }
}
