package com.trib3.server.graphql

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.GraphQL
import org.easymock.EasyMock
import org.testng.annotations.Test

class GraphQLWebSocketCreatorTest {
    @Test
    fun testSocketCreation() {
        val graphQL = EasyMock.mock<GraphQL>(GraphQL::class.java)
        val mapper = ObjectMapper()
        val creator = GraphQLWebSocketCreator(graphQL, mapper)
        assertThat(creator.graphQL).isEqualTo(graphQL)
        assertThat(creator.objectMapper).isEqualTo(mapper)
        val socket = creator.createWebSocket(null, null)
        assertThat(socket).isInstanceOf(GraphQLWebSocket::class)
        assertThat((socket as GraphQLWebSocket).graphQL).isEqualTo(graphQL)
        assertThat(socket.objectMapper).isEqualTo(mapper)
        // mapper writes without pretty printing, writer writes with pretty printing
        assertThat(socket.objectMapper.writeValueAsString(mapOf("a" to "b"))).isEqualTo("""{"a":"b"}""")
        assertThat(socket.objectWriter.writeValueAsString(mapOf("a" to "b"))).isEqualTo(
            """{
            |  "a" : "b"
            |}""".trimMargin()
        )
    }
}
