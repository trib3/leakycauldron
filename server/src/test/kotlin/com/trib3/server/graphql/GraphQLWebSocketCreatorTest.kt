package com.trib3.server.graphql

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.config.ConfigLoader
import com.trib3.config.KMSStringSelectReader
import graphql.GraphQL
import org.easymock.EasyMock
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
import org.testng.annotations.Test

class GraphQLWebSocketCreatorTest {
    @Test
    fun testSocketCreation() {
        val graphQL = EasyMock.mock<GraphQL>(GraphQL::class.java)
        val mapper = ObjectMapper()
        val creator = GraphQLWebSocketCreator(graphQL, mapper, GraphQLConfig(ConfigLoader(KMSStringSelectReader(null))))
        assertThat(creator.graphQL).isEqualTo(graphQL)
        assertThat(creator.objectMapper).isEqualTo(mapper)

        val request = EasyMock.mock<ServletUpgradeRequest>(ServletUpgradeRequest::class.java)
        val response = EasyMock.mock<ServletUpgradeResponse>(ServletUpgradeResponse::class.java)
        EasyMock.expect(response.setAcceptedSubProtocol("graphql-ws")).once()
        EasyMock.replay(graphQL, request, response)
        val socket = creator.createWebSocket(request, response)
        EasyMock.verify(response)
        assertThat(socket).isInstanceOf(GraphQLWebSocket::class)
        assertThat((socket as GraphQLWebSocket).graphQL).isEqualTo(graphQL)
        assertThat(socket.objectMapper).isEqualTo(mapper)
        assertThat(socket.keepAliveIntervalSeconds).isEqualTo(creator.graphQLConfig.keepAliveIntervalSeconds)
        // mapper writes without pretty printing, writer writes with pretty printing
        assertThat(socket.objectMapper.writeValueAsString(mapOf("a" to "b"))).isEqualTo("""{"a":"b"}""")
        assertThat(socket.objectWriter.writeValueAsString(mapOf("a" to "b"))).isEqualTo(
            """{
            |  "a" : "b"
            |}""".trimMargin()
        )
    }
}
