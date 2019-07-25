package com.trib3.server.graphql

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.message
import com.expedia.graphql.SchemaGeneratorConfig
import com.expedia.graphql.TopLevelObject
import com.expedia.graphql.toSchema
import com.fasterxml.jackson.module.kotlin.readValue
import com.trib3.json.ObjectMapperProvider
import graphql.ExecutionInput
import graphql.GraphQL
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.toFlowable
import io.reactivex.schedulers.Schedulers
import org.easymock.EasyMock
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.common.WebSocketRemoteEndpoint
import org.reactivestreams.Publisher
import org.testng.annotations.Test
import java.util.concurrent.CountDownLatch

class SocketQuery {
    fun q(): List<String> {
        return listOf("1", "2", "3")
    }

    fun v(len: Int): List<String> {
        return (1..len).toList().map(Int::toString)
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

    fun inf(): Publisher<String> {
        return object : Iterator<String> {
            var value = 1
            override fun hasNext(): Boolean {
                return true
            }

            override fun next(): String {
                val toReturn = value.toString()
                value += 1
                return toReturn
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
    val mapper = ObjectMapperProvider().get()

    /**
     * get a socket configured with the test graphQL and object mapper
     * default to using the "trampoline" (ie, current thread) scheduler
     * so that we don't have to deal with multiple threads unless necessary
     */
    private fun getSocket(scheduler: Scheduler = Schedulers.trampoline()): GraphQLWebSocket {
        return GraphQLWebSocket(graphQL, mapper, scheduler = scheduler)
    }

    @Test
    fun testSocketQuery() {
        val socket = getSocket()
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""q" : [ "1", "2", "3" ]"""),
                    EasyMock.and(
                        EasyMock.contains(""""type" : "data""""),
                        EasyMock.contains(""""id" : "simplequery"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "complete""""),
                    EasyMock.contains(""""id" : "simplequery"""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText("""{"type": "start", "id": "simplequery", "payload": {"query": "query { q }"}}""")
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testSocketVariableQuery() {
        val socket = getSocket()
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""v" : [ "1", "2", "3" ]"""),
                    EasyMock.and(
                        EasyMock.contains(""""type" : "data""""),
                        EasyMock.contains(""""id" : "simplequery"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "complete""""),
                    EasyMock.contains(""""id" : "simplequery"""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText(
            """
            {"type": "start",
            "id": "simplequery",
            "payload": {"query": "query(${'$'}len:Int!) { v(len: ${'$'}len) }",
                        "variables": {"len": 3}}}""".trimIndent()
        )
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testSocketQueryError() {
        val socket = getSocket()
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        val errorCapture = EasyMock.newCapture<String>()
        EasyMock.expect(mockRemote.sendString(EasyMock.capture(errorCapture))).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "complete""""),
                    EasyMock.contains(""""id" : "errorquery"""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText("""{"type": "start", "id": "errorquery", "payload": {"query": "query { e }"}}""")
        EasyMock.verify(mockRemote, mockSession)
        val errorMessage = mapper.readValue<OperationMessage<Map<*, *>>>(errorCapture.value)
        assertThat(errorMessage.type).isEqualTo(OperationType.GQL_DATA)
        assertThat(errorMessage.id).isEqualTo("errorquery")
        val error = errorMessage.payload!!
        assertThat(error["errors"]).isNotNull().isInstanceOf(List::class)
        assertThat(error["errors"] as List<*>).hasSize(1)
        val firstError = (error["errors"] as List<*>)[0]
        assertThat(firstError).isNotNull().isInstanceOf(Map::class)
        assertThat((firstError as Map<*, *>)["message"]).isNotNull().isInstanceOf(String::class)
        val message = firstError["message"] as String
        assertThat(message).contains("forced exception")
    }

    @Test
    fun testSocketExecutionError() {
        val mockGraphQL = EasyMock.mock<GraphQL>(GraphQL::class.java)
        val socket = GraphQLWebSocket(mockGraphQL, mapper, scheduler = Schedulers.trampoline())
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(mockGraphQL.execute(EasyMock.anyObject<ExecutionInput>()))
            .andThrow(IllegalStateException("ExecutionError"))
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "error""""),
                    EasyMock.contains(""""id" : "executionerror"""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession, mockGraphQL)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText("""{"type": "start", "id": "executionerror", "payload": {"query": "invalid!"}}""")
        EasyMock.verify(mockRemote, mockSession, mockGraphQL)
    }

    @Test
    fun testSocketSubscription() {
        val socket = getSocket()
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""s" : "1""""),
                    EasyMock.and(
                        EasyMock.contains(""""type" : "data""""),
                        EasyMock.contains(""""id" : "simplesubscription"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""s" : "2""""),
                    EasyMock.and(
                        EasyMock.contains(""""type" : "data""""),
                        EasyMock.contains(""""id" : "simplesubscription"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""s" : "3""""),
                    EasyMock.and(
                        EasyMock.contains(""""type" : "data""""),
                        EasyMock.contains(""""id" : "simplesubscription"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "complete""""),
                    EasyMock.contains(""""id" : "simplesubscription"""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText(
            """
            {"type": "start",
             "id": "simplesubscription",
             "payload": {"query": "subscription { s }"}}""".trimIndent()
        )
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testSocketSubscriptionError() {
        val socket = getSocket()
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""e" : "1""""),
                    EasyMock.and(
                        EasyMock.contains(""""type" : "data""""),
                        EasyMock.contains(""""id" : "errorsubscription"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""e" : "2""""),
                    EasyMock.and(
                        EasyMock.contains(""""type" : "data""""),
                        EasyMock.contains(""""id" : "errorsubscription"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "error""""),
                    EasyMock.contains(""""id" : "errorsubscription"""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText(
            """
            {"type": "start",
             "id": "errorsubscription",
             "payload": {"query": "subscription { e }"}}""".trimIndent()
        )
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testGenericErrors() {
        val socket = GraphQLWebSocket(null, mapper) // unconfigured graphQL instance
        assertThat(socket.scheduler).isEqualTo(Schedulers.io())
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "error""""),
                    EasyMock.contains(""""id" : "unknownoperation"""")
                )
            )
        ).once()
        EasyMock.expect(mockSession.close(EasyMock.eq(StatusCode.SERVER_ERROR), EasyMock.contains("boom"))).once()

        EasyMock.replay(mockRemote, mockSession)

        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText(
            """
            {"type": "unknown",
             "id": "unknownoperation"}""".trimIndent()
        )
        socket.onWebSocketError(IllegalStateException("boom"))
        EasyMock.verify(mockRemote, mockSession)

        assertThat {
            socket.onWebSocketText(
                """
                {"type": "start",
                 "id": "unconfiguredquery",
                 "payload": {"query": "query { q }"}}""".trimIndent()
            )
        }.isFailure().message().isNotNull().contains("not configured")
    }

    @Test
    fun testConnectAck() {
        val socket = getSocket()
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "connection_ack""""),
                    EasyMock.contains(""""id" : "connect"""")
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "ka""""),
                    EasyMock.contains(""""id" : "connect"""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText("""{"type": "connection_init", "id": "connect"}""")

        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testRequestTerminate() {
        val socket = getSocket()
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockSession.close(StatusCode.NORMAL, "Termination Requested")
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText("""{"type": "connection_terminate", "id": "terminate"}""")

        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testWebSocketClose() {
        // use Schedulers.computation() since we're using the infinite subscription stream
        // and want to be able to call `onWebSocketClose` while the query is running
        val socket = getSocket(Schedulers.computation())
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(mockRemote.sendString(EasyMock.anyString())).anyTimes()
        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText("""{"type": "connection_init", "id": "connect"}""")
        val keepAliveInterval = socket.keepAliveInterval
        assertThat(keepAliveInterval).isNotNull()
        socket.onWebSocketText("""{"type": "connection_init", "id": "connect"}""")
        assertThat(socket.keepAliveInterval).isEqualTo(keepAliveInterval)
        socket.onWebSocketText("""{"type": "start", "id": "run", "payload": {"query": "subscription {inf}"}}""")
        assertThat(socket.runningQuerySubscriber).isNotNull()
        socket.onWebSocketClose(StatusCode.NORMAL, "externally closed")
        assertThat(socket.session).isNull()
        assertThat(socket.keepAliveInterval).isNull()
        assertThat(socket.runningQuerySubscriber).isNull()
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testKeepAlive() {
        val socket = getSocket()
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        val keepAliveCallbacks = KeepAliveCallbacks(socket, "katest")
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "ka""""),
                    EasyMock.contains(""""id" : "katest"""")
                )
            )
        ).once()
        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        keepAliveCallbacks.onInterval(1)
        keepAliveCallbacks.onError(IllegalStateException())
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testStopQuery() {
        val latch = CountDownLatch(1)
        // override the socket to signal the test that it's sent at least one data message
        // and use Schedulers.computation() since we're using the infinite subscription stream
        val socket = object : GraphQLWebSocket(graphQL, mapper, scheduler = Schedulers.computation()) {
            override fun sendMessage(message: OperationMessage<*>) {
                super.sendMessage(message)
                if (message.type == OperationType.GQL_DATA) {
                    latch.countDown()
                }
            }
        }
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""inf" : """"),
                    EasyMock.and(
                        EasyMock.contains(""""type" : "data""""),
                        EasyMock.contains(""""id" : "longsubscription"""")
                    )
                )
            )
        ).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "error""""),
                    EasyMock.contains(""""id" : "secondquery"""")
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""inf" : """"),
                    EasyMock.and(
                        EasyMock.contains(""""type" : "data""""),
                        EasyMock.contains(""""id" : "longsubscription"""")
                    )
                )
            )
        ).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "complete""""),
                    EasyMock.contains(""""id" : "longsubscription"""")
                )
            )
        ).once()
        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText(
            """
            {"type": "start",
             "id": "longsubscription",
             "payload": {"query": "subscription { inf }"}}""".trimIndent()
        )
        socket.onWebSocketText(
            """
            {"type": "start",
             "id": "secondquery",
             "payload": {"query": "subscription { s }"}}""".trimIndent()
        )
        assertThat {
            socket.onQueryFinished(
                GraphQLQuerySubscriber(socket, "notactuallyrunning", "test"),
                0
            )
        }.isFailure()
            .message()
            .isNotNull()
            .isEqualTo("Query notactuallyrunning but we don't think it's running")
        latch.await()
        socket.onWebSocketText(
            """
            {"type": "stop",
             "id": "longsubscription"}""".trimIndent()
        )
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testStopWrongQuery() {
        val socket = getSocket()
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "error""""),
                    EasyMock.contains(""""id" : "unknownquery"""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText(
            """
            {"type": "stop",
             "id": "unknownquery"}""".trimIndent()
        )
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testStartWithoutId() {
        val socket = getSocket()
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "error""""),
                    EasyMock.contains(""""payload" : "Must pass a message id""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText(
            """
            {"type": "start",
             "id": null}""".trimIndent()
        )
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testBadMessage() {
        val socket = getSocket()
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "error""""),
                    EasyMock.contains(""""payload" : "Unrecognized token""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.onWebSocketConnect(mockSession)
        socket.onWebSocketText("not json!")
        EasyMock.verify(mockRemote, mockSession)
    }
}
