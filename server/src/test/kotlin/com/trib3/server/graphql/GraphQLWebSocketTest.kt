package com.trib3.server.graphql

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.message
import com.expedia.graphql.SchemaGeneratorConfig
import com.expedia.graphql.TopLevelObject
import com.expedia.graphql.toSchema
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import graphql.GraphQL
import io.reactivex.rxkotlin.toFlowable
import org.easymock.EasyMock
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.common.WebSocketRemoteEndpoint
import org.reactivestreams.Publisher
import org.testng.annotations.Test

class SocketQuery {
    fun q(): List<String> {
        return listOf("1", "2", "3")
    }

    fun e(): List<String> {
        throw IllegalStateException("forced exception")
    }
}

class SocketSubscription {
    fun s(): Publisher<String> {
        return listOf("1", "2", "3").toFlowable()
    }

    fun e(): Publisher<String> {
        return object : Iterator<String> {
            var value = 1
            override fun hasNext(): Boolean {
                return value < 4
            }

            override fun next(): String {
                val toReturn = value.toString()
                value += 1
                return if (toReturn == "3") throw IllegalStateException("forced exception") else toReturn
            }
        }.toFlowable()
    }
}

class GraphQLWebSocketTest {

    val graphQL = GraphQL.newGraphQL(
        toSchema(
            SchemaGeneratorConfig(listOf()),
            listOf(TopLevelObject(SocketQuery())),
            listOf(),
            listOf(TopLevelObject(SocketSubscription()))
        )
    ).build()

    @Test
    fun testSocketQuery() {
        val socket = GraphQLWebSocket(graphQL, ObjectMapper())
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(mockRemote.sendString(EasyMock.contains(""""q" : [ "1", "2", "3" ]"""))).once()
        EasyMock.expect(mockSession.close(EasyMock.eq(StatusCode.NORMAL), EasyMock.anyString())).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText("query { q }")
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testSocketQueryError() {
        val socket = GraphQLWebSocket(graphQL, ObjectMapper())
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        val errorCapture = EasyMock.newCapture<String>()
        EasyMock.expect(mockRemote.sendString(EasyMock.capture(errorCapture))).once()
        EasyMock.expect(mockSession.close(EasyMock.eq(StatusCode.SERVER_ERROR), EasyMock.anyString())).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText("query { e }")
        EasyMock.verify(mockRemote, mockSession)
        val error = ObjectMapper().readValue<Map<String, Any>>(errorCapture.value)
        assertThat(error["errors"]).isNotNull().isInstanceOf(List::class)
        assertThat(error["errors"] as List<*>).hasSize(1)
        val firstError = (error["errors"] as List<*>)[0]
        assertThat(firstError).isNotNull().isInstanceOf(Map::class)
        assertThat((firstError as Map<*, *>)["message"]).isNotNull().isInstanceOf(String::class)
        val message = firstError["message"] as String
        assertThat(message).contains("forced exception")
    }

    @Test
    fun testSocketSubscription() {
        val socket = GraphQLWebSocket(graphQL, ObjectMapper())
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(mockRemote.sendString(EasyMock.contains(""""s" : "1"""))).once()
        EasyMock.expect(mockRemote.sendString(EasyMock.contains(""""s" : "2"""))).once()
        EasyMock.expect(mockRemote.sendString(EasyMock.contains(""""s" : "3"""))).once()
        EasyMock.expect(mockSession.close(EasyMock.eq(StatusCode.NORMAL), EasyMock.anyString())).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText("subscription { s }")
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testSocketSubscriptionError() {
        val socket = GraphQLWebSocket(graphQL, ObjectMapper())
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(mockRemote.sendString(EasyMock.contains(""""e" : "1"""))).once()
        EasyMock.expect(mockRemote.sendString(EasyMock.contains(""""e" : "2"""))).once()
        EasyMock.expect(mockSession.close(EasyMock.eq(StatusCode.SERVER_ERROR), EasyMock.anyString())).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText("subscription { e }")
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testGenericErrors() {
        val socket = GraphQLWebSocket(null, ObjectMapper())
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(mockSession.close(EasyMock.eq(StatusCode.SERVER_ERROR), EasyMock.contains("boom"))).once()

        EasyMock.replay(mockRemote, mockSession)

        socket.onWebSocketConnect(mockSession)
        assertThat {
            socket.onWebSocketText("query")
        }.thrownError { message().isNotNull().contains("not configured") }
        socket.onWebSocketError(IllegalStateException("boom"))
        EasyMock.verify(mockRemote, mockSession)
    }
}
