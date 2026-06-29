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
import org.eclipse.jetty.http.HttpField
import org.eclipse.jetty.http.HttpFields
import org.eclipse.jetty.http.HttpURI
import org.eclipse.jetty.security.AuthenticationState
import org.eclipse.jetty.security.UserIdentity
import org.eclipse.jetty.security.internal.DefaultUserIdentity
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.websocket.core.server.ServerUpgradeRequest
import org.eclipse.jetty.websocket.core.server.ServerUpgradeResponse
import org.testng.annotations.Test
import java.security.Principal

class GraphQLWebSocketCreatorTest {
    val mapper = ObjectMapper()

    private fun getCreatorAndGraphQL(): Pair<GraphQLWebSocketCreator, GraphQL> {
        val graphQL = LeakyMock.mock<GraphQL>()
        val creator =
            GraphQLWebSocketCreator(
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
        EasyMock.expect(request.httpURI).andReturn(HttpURI.from("http://localhost:12345/app/graphql")).anyTimes()
        EasyMock.expect(request.method).andReturn("GET").anyTimes()
        val mockHeaders = HttpFields.build()
        EasyMock.expect(request.headers).andReturn(mockHeaders).anyTimes()
        EasyMock.expect(request.hasSubProtocol("graphql-transport-ws")).andReturn(false).once()
        EasyMock.expect(response.setAcceptedSubProtocol("graphql-ws")).once()
        EasyMock.expect(request.getAttribute("org.eclipse.jetty.server.Request\$AuthenticationState")).andReturn(null)
        EasyMock.replay(graphQL, request, response)
        val socket = creator.createWebSocket(request, response, Callback.NOOP)
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
        EasyMock.expect(request.httpURI).andReturn(HttpURI.from("http://localhost:12345/app/graphql")).anyTimes()
        EasyMock.expect(request.method).andReturn("GET").anyTimes()
        EasyMock.expect(request.headers).andReturn(HttpFields.build()).anyTimes()
        EasyMock.expect(request.hasSubProtocol("graphql-transport-ws")).andReturn(true).once()
        EasyMock.expect(response.setAcceptedSubProtocol("graphql-transport-ws")).once()
        EasyMock.expect(request.getAttribute("org.eclipse.jetty.server.Request\$AuthenticationState")).andReturn(null)
        EasyMock.replay(graphQL, request, response)
        val socket = creator.createWebSocket(request, response, Callback.NOOP)
        EasyMock.verify(graphQL, request, response)
        assertThat(socket).isNotNull().isInstanceOf(GraphQLWebSocketAdapter::class)
        assertThat((socket as GraphQLWebSocketAdapter).objectMapper).isEqualTo(mapper)
        assertThat(socket.channel).isNotNull()
        assertThat(socket.subProtocol).isEqualTo(GraphQLWebSocketSubProtocol.GRAPHQL_WS_PROTOCOL)
    }

    @Test
    fun testRequestConversionNullHeadersAndAuth() {
        val creator = getCreatorAndGraphQL().first
        val mockRequest = LeakyMock.mock<ServerUpgradeRequest>()
        val mockServletRequest = LeakyMock.mock<HttpServletRequest>()
        EasyMock.expect(mockRequest.httpURI).andReturn(HttpURI.from("http://test.com")).anyTimes()
        EasyMock.expect(mockRequest.method).andReturn("GET").anyTimes()
        EasyMock.expect(mockRequest.headers).andReturn(null).anyTimes()
        EasyMock
            .expect(
                mockRequest.getAttribute("org.eclipse.jetty.server.Request\$AuthenticationState"),
            ).andReturn(null)
        EasyMock.expect(mockRequest.isSecure).andReturn(true).anyTimes()
        EasyMock.expect(mockServletRequest.authType).andReturn("Basic").anyTimes()
        EasyMock.replay(mockRequest, mockServletRequest)
        val convertedRequest = creator.convertToRequestContext(mockRequest)
        assertThat(convertedRequest.method).isEqualTo(mockRequest.method)
        assertThat(convertedRequest.uriInfo.baseUri).isEqualTo(mockRequest.httpURI.toURI())
        assertThat(convertedRequest.headers).isEmpty()
        assertThat(convertedRequest.securityContext.userPrincipal).isNull()
        assertThat(convertedRequest.securityContext.isSecure).isTrue()
        assertThat(convertedRequest.securityContext.isUserInRole("ADMIN")).isFalse()
        assertThat(convertedRequest.securityContext.authenticationScheme).isNull()
        EasyMock.verify(mockRequest, mockServletRequest)
    }

    @Test
    fun testRequestConversionWithHeadersAndAuth() {
        val creator = getCreatorAndGraphQL().first
        val mockRequest = LeakyMock.mock<ServerUpgradeRequest>()
        val mockServletRequest = LeakyMock.mock<HttpServletRequest>()
        EasyMock.expect(mockRequest.httpURI).andReturn(HttpURI.from("http://test.com")).anyTimes()
        EasyMock.expect(mockRequest.method).andReturn("GET").anyTimes()
        EasyMock
            .expect(mockRequest.headers)
            .andReturn(
                HttpFields.build(
                    HttpFields.from(
                        HttpField(null, "h1", "v1"),
                        HttpField(null, "h1", "v2"),
                        HttpField(null, "h2", "v3"),
                    ),
                ),
            ).anyTimes()
        EasyMock.expect(mockRequest.getAttribute("org.eclipse.jetty.server.Request\$AuthenticationState")).andReturn(
            object : AuthenticationState.Succeeded {
                override fun getAuthenticationType(): String = "Basic"

                override fun getUserIdentity(): UserIdentity? =
                    DefaultUserIdentity(
                        null,
                        object : Principal {
                            override fun getName(): String = "Billy"
                        },
                        arrayOf(),
                    )

                override fun isUserInRole(role: String?): Boolean = "ADMIN" == role

                override fun logout(
                    request: Request?,
                    response: Response?,
                ) {
                }
            },
        )
        EasyMock.expect(mockRequest.isSecure).andReturn(true).anyTimes()
        EasyMock.expect(mockServletRequest.authType).andReturn("Basic").anyTimes()
        EasyMock.replay(mockRequest, mockServletRequest)
        val convertedRequest = creator.convertToRequestContext(mockRequest)
        assertThat(convertedRequest.method).isEqualTo(mockRequest.method)
        assertThat(convertedRequest.uriInfo.baseUri).isEqualTo(mockRequest.httpURI.toURI())
        assertThat(convertedRequest.headers["h1"]).isEqualTo(listOf("v1", "v2"))
        assertThat(convertedRequest.headers["h2"]).isEqualTo(listOf("v3"))
        assertThat(convertedRequest.securityContext.userPrincipal.name).isEqualTo("Billy")
        assertThat(convertedRequest.securityContext.isSecure).isTrue()
        assertThat(convertedRequest.securityContext.isUserInRole("ADMIN")).isTrue()
        assertThat(convertedRequest.securityContext.isUserInRole("MEGAADMIN")).isFalse()
        assertThat(convertedRequest.securityContext.authenticationScheme).isEqualTo("Basic")
        EasyMock.verify(mockRequest, mockServletRequest)
    }
}
