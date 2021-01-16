package com.trib3.graphql.websocket

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.config.ConfigLoader
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.GraphQLConfigTest
import com.trib3.testing.LeakyMock
import graphql.GraphQL
import org.easymock.EasyMock
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
import org.testng.annotations.Test
import javax.ws.rs.container.ContainerRequestContext

class GraphQLWebSocketCreatorTest {
    @Test
    fun testSocketCreation() {
        val graphQL = LeakyMock.mock<GraphQL>()
        val mapper = ObjectMapper()
        val creatorFactory = GraphQLWebSocketCreatorFactory(graphQL, mapper, GraphQLConfig(ConfigLoader()))
        val mockContext = LeakyMock.mock<ContainerRequestContext>()
        val creator = creatorFactory.getCreator(mockContext)
        assertThat(creator).isInstanceOf(GraphQLWebSocketCreator::class)
        assertThat((creator as GraphQLWebSocketCreator).graphQL).isEqualTo(graphQL)
        assertThat(creator.objectMapper).isEqualTo(mapper)
        assertThat(creator.graphQLConfig.keepAliveIntervalSeconds).isEqualTo(GraphQLConfigTest.DEFAULT_KEEPALIVE)
        assertThat(creator.containerRequestContext).isEqualTo(mockContext)
        // test optional params constructor works fine
        val secondCreator = GraphQLWebSocketCreator(graphQL, mapper, creator.graphQLConfig, mockContext)
        assertThat(secondCreator.graphQL).isEqualTo(graphQL)
        assertThat(secondCreator.graphQLConfig.keepAliveIntervalSeconds).isEqualTo(GraphQLConfigTest.DEFAULT_KEEPALIVE)

        val request = LeakyMock.mock<ServletUpgradeRequest>()
        val response = LeakyMock.mock<ServletUpgradeResponse>()
        EasyMock.expect(response.setAcceptedSubProtocol("graphql-ws")).once()
        EasyMock.replay(graphQL, request, response)
        val socket = creator.createWebSocket(request, response)
        EasyMock.verify(response)
        assertThat(socket).isInstanceOf(GraphQLWebSocketAdapter::class)
        assertThat((socket as GraphQLWebSocketAdapter).objectMapper).isEqualTo(mapper)
        assertThat(socket.channel).isNotNull()
        // mapper writes without pretty printing, writer writes with pretty printing
        assertThat(socket.objectMapper.writeValueAsString(mapOf("a" to "b"))).isEqualTo("""{"a":"b"}""")
        assertThat(socket.objectWriter.writeValueAsString(mapOf("a" to "b"))).isEqualTo(
            """{
            |  "a" : "b"
            |}""".trimMargin()
        )
    }
}
