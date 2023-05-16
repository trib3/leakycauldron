package com.trib3.graphql.websocket

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.config.ConfigLoader
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.GraphQLConfigTest
import com.trib3.testing.LeakyMock
import graphql.GraphQL
import jakarta.servlet.http.HttpServletRequest
import org.easymock.EasyMock
import org.eclipse.jetty.websocket.core.server.ServerUpgradeRequest
import org.eclipse.jetty.websocket.core.server.ServerUpgradeResponse
import org.testng.annotations.Test
import java.net.URI

class GraphQLWebSocketCreatorTest {
    val mapper = ObjectMapper()

    private fun getCreatorAndGraphQL(): Pair<GraphQLWebSocketCreator, GraphQL> {
        val graphQL = LeakyMock.mock<GraphQL>()
        val creator = GraphQLWebSocketCreator(
            graphQL,
            mapper,
            GraphQLConfig(ConfigLoader()),
        )
        assertThat(creator).isInstanceOf(GraphQLWebSocketCreator::class)
        assertThat(creator.graphQL).isEqualTo(graphQL)
        assertThat(creator.objectMapper).isEqualTo(mapper)
        assertThat(creator.graphQLConfig.keepAliveIntervalSeconds).isEqualTo(GraphQLConfigTest.DEFAULT_KEEPALIVE)
        return Pair(creator, graphQL)
    }

    @Test
    fun testSocketCreation() {
        val (creator, graphQL) = getCreatorAndGraphQL()

        val request = LeakyMock.mock<ServerUpgradeRequest>()
        val response = LeakyMock.mock<ServerUpgradeResponse>()
        EasyMock.expect(request.requestURI).andReturn(URI("http://localhost:12345/app/graphql")).anyTimes()
        EasyMock.expect(request.method).andReturn("GET").anyTimes()
        EasyMock.expect(request.headersMap).andReturn(emptyMap()).anyTimes()
        EasyMock.expect(request.getHeader("Origin")).andReturn(null).anyTimes()
        EasyMock.expect(request.hasSubProtocol("graphql-transport-ws")).andReturn(false).once()
        EasyMock.expect(response.setAcceptedSubProtocol("graphql-ws")).once()
        EasyMock.replay(graphQL, request, response)
        val socket = creator.createWebSocket(request, response)
        EasyMock.verify(graphQL, request, response)
        assertThat(socket).isNotNull().isInstanceOf(GraphQLWebSocketAdapter::class)
        assertThat((socket as GraphQLWebSocketAdapter).objectMapper).isEqualTo(mapper)
        assertThat(socket.channel).isNotNull()
        assertThat(socket.subProtocol).isEqualTo(GraphQLWebSocketSubProtocol.APOLLO_PROTOCOL)
        // mapper writes without pretty printing, writer writes with pretty printing
        assertThat(socket.objectMapper.writeValueAsString(mapOf("a" to "b"))).isEqualTo("""{"a":"b"}""")
        assertThat(socket.objectWriter.writeValueAsString(mapOf("a" to "b"))).isEqualTo(
            """{
            |  "a" : "b"
            |}
            """.trimMargin(),
        )
    }

    @Test
    fun testGraphQlWsCreation() {
        val (creator, graphQL) = getCreatorAndGraphQL()

        val request = LeakyMock.mock<ServerUpgradeRequest>()
        val response = LeakyMock.mock<ServerUpgradeResponse>()
        EasyMock.expect(request.requestURI).andReturn(URI("http://localhost:12345/app/graphql")).anyTimes()
        EasyMock.expect(request.method).andReturn("GET").anyTimes()
        EasyMock.expect(request.headersMap).andReturn(emptyMap()).anyTimes()
        EasyMock.expect(request.getHeader("Origin")).andReturn(null).anyTimes()
        EasyMock.expect(request.hasSubProtocol("graphql-transport-ws")).andReturn(true).once()
        EasyMock.expect(response.setAcceptedSubProtocol("graphql-transport-ws")).once()
        EasyMock.replay(graphQL, request, response)
        val socket = creator.createWebSocket(request, response)
        EasyMock.verify(graphQL, request, response)
        assertThat(socket).isNotNull().isInstanceOf(GraphQLWebSocketAdapter::class)
        assertThat((socket as GraphQLWebSocketAdapter).objectMapper).isEqualTo(mapper)
        assertThat(socket.channel).isNotNull()
        assertThat(socket.subProtocol).isEqualTo(GraphQLWebSocketSubProtocol.GRAPHQL_WS_PROTOCOL)
    }

    @Test
    fun testRequestConversionNullHeaders() {
        val creator = getCreatorAndGraphQL().first
        val mockRequest = LeakyMock.mock<ServerUpgradeRequest>()
        val mockServletRequest = LeakyMock.mock<HttpServletRequest>()
        EasyMock.expect(mockRequest.requestURI).andReturn(URI("http://test.com")).anyTimes()
        EasyMock.expect(mockRequest.method).andReturn("GET").anyTimes()
        EasyMock.expect(mockRequest.headersMap).andReturn(null).anyTimes()
        EasyMock.expect(mockRequest.userPrincipal).andReturn(null).anyTimes()
        EasyMock.expect(mockRequest.isUserInRole("ADMIN")).andReturn(false).anyTimes()
        EasyMock.expect(mockRequest.isSecure).andReturn(true).anyTimes()
        EasyMock.expect(mockRequest.httpServletRequest).andReturn(mockServletRequest).anyTimes()
        EasyMock.expect(mockServletRequest.authType).andReturn("Basic").anyTimes()
        EasyMock.replay(mockRequest, mockServletRequest)
        val convertedRequest = creator.convertToRequestContext(mockRequest)
        assertThat(convertedRequest.method).isEqualTo(mockRequest.method)
        assertThat(convertedRequest.uriInfo.baseUri).isEqualTo(mockRequest.requestURI)
        assertThat(convertedRequest.headers).isEmpty()
        assertThat(convertedRequest.securityContext.userPrincipal).isNull()
        assertThat(convertedRequest.securityContext.isSecure).isTrue()
        assertThat(convertedRequest.securityContext.isUserInRole("ADMIN")).isFalse()
        assertThat(convertedRequest.securityContext.authenticationScheme).isEqualTo("Basic")
        EasyMock.verify(mockRequest, mockServletRequest)
    }

    @Test
    fun testRequestConversionWithHeaders() {
        val creator = getCreatorAndGraphQL().first
        val mockRequest = LeakyMock.mock<ServerUpgradeRequest>()
        val mockServletRequest = LeakyMock.mock<HttpServletRequest>()
        EasyMock.expect(mockRequest.requestURI).andReturn(URI("http://test.com")).anyTimes()
        EasyMock.expect(mockRequest.method).andReturn("GET").anyTimes()
        EasyMock.expect(mockRequest.headersMap).andReturn(mapOf("h1" to listOf("v1", "v2"), "h2" to listOf("v3")))
            .anyTimes()
        EasyMock.expect(mockRequest.userPrincipal).andReturn(null).anyTimes()
        EasyMock.expect(mockRequest.isUserInRole("ADMIN")).andReturn(false).anyTimes()
        EasyMock.expect(mockRequest.isSecure).andReturn(true).anyTimes()
        EasyMock.expect(mockRequest.httpServletRequest).andReturn(mockServletRequest).anyTimes()
        EasyMock.expect(mockServletRequest.authType).andReturn("Basic").anyTimes()
        EasyMock.replay(mockRequest, mockServletRequest)
        val convertedRequest = creator.convertToRequestContext(mockRequest)
        assertThat(convertedRequest.method).isEqualTo(mockRequest.method)
        assertThat(convertedRequest.uriInfo.baseUri).isEqualTo(mockRequest.requestURI)
        assertThat(convertedRequest.headers["h1"]).isEqualTo(listOf("v1", "v2"))
        assertThat(convertedRequest.headers["h2"]).isEqualTo(listOf("v3"))
        assertThat(convertedRequest.securityContext.userPrincipal).isNull()
        assertThat(convertedRequest.securityContext.isSecure).isTrue()
        assertThat(convertedRequest.securityContext.isUserInRole("ADMIN")).isFalse()
        assertThat(convertedRequest.securityContext.authenticationScheme).isEqualTo("Basic")
        EasyMock.verify(mockRequest, mockServletRequest)
    }
}
