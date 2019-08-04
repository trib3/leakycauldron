package com.trib3.server.graphql

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.expedia.graphql.SchemaGeneratorConfig
import com.expedia.graphql.TopLevelObject
import com.expedia.graphql.toSchema
import com.trib3.config.ConfigLoader
import com.trib3.config.KMSStringSelectReader
import com.trib3.json.ObjectMapperProvider
import graphql.ExecutionInput
import graphql.GraphQL
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.toFlowable
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import org.easymock.EasyMock
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.common.WebSocketRemoteEndpoint
import org.reactivestreams.Publisher
import org.testng.annotations.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    val testGraphQL = GraphQL.newGraphQL(
        toSchema(
            SchemaGeneratorConfig(listOf()),
            listOf(TopLevelObject(SocketQuery())),
            listOf(),
            listOf(TopLevelObject(SocketSubscription()))
        )
    ).build()
    val mapper = ObjectMapperProvider().get()
    val config = GraphQLConfig(ConfigLoader(KMSStringSelectReader(null)))

    /**
     * get a socket configured with the test graphQL and object mapper
     * default to using the "trampoline" (ie, current thread) scheduler
     * so that we don't have to deal with multiple threads unless necessary
     */
    private fun getSocket(
        graphQL: GraphQL? = testGraphQL,
        scheduler: Scheduler = Schedulers.trampoline(),
        keepAliveScheduler: Scheduler = Schedulers.computation()
    ): GraphQLWebSocketSubscriber {
        val subscriber = GraphQLWebSocketSubscriber(
            graphQL,
            config,
            scheduler,
            keepAliveScheduler
        )
        GraphQLWebSocketCreator(graphQL, mapper, config)
            .createWebSocket(subscriber, scheduler)
        return subscriber
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
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText(
            """{"type": "start", "id": "simplequery", "payload": {"query": "query { q }"}}"""
        )
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
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText(
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
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText("""{"type": "start", "id": "errorquery", "payload": {"query": "query { e }"}}""")
        EasyMock.verify(mockRemote, mockSession)
        assertThat(errorCapture.value).contains("forced exception")
    }

    @Test
    fun testSocketExecutionError() {
        val mockGraphQL = EasyMock.mock<GraphQL>(GraphQL::class.java)
        val socket = getSocket(mockGraphQL)
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
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText(
            """{"type": "start", "id": "executionerror", "payload": {"query": "invalid!"}}"""
        )
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
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText(
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
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText(
            """
            {"type": "start",
             "id": "errorsubscription",
             "payload": {"query": "subscription { e }"}}""".trimIndent()
        )
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testGenericErrors() {
        val socket = getSocket(null) // unconfigured graphQL instance
        assertThat(socket.scheduler).isEqualTo(Schedulers.trampoline())
        assertThat(socket.keepAliveScheduler).isEqualTo(Schedulers.computation())
        assertThat(socket.graphQLConfig).isEqualTo(config)
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
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "error""""),
                    EasyMock.and(
                        EasyMock.contains(""""id" : "unconfiguredquery""""),
                        EasyMock.contains("not configured")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "error""""),
                    EasyMock.and(
                        EasyMock.contains(""""id" : "invalidtype""""),
                        EasyMock.contains(""""payload" : "Unknown message type"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "error""""),
                    EasyMock.and(
                        EasyMock.contains(""""id" : "badpayload""""),
                        EasyMock.contains(""""payload" : "Invalid payload for query"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(mockSession.close(EasyMock.eq(StatusCode.SERVER_ERROR), EasyMock.contains("boom"))).once()
        EasyMock.expect(mockSession.close(EasyMock.anyInt(), EasyMock.anyString())).anyTimes()

        EasyMock.replay(mockRemote, mockSession)

        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText(
            """
            {"type": "unknown",
             "id": "unknownoperation",
             "payload": null}""".trimIndent()
        )
        socket.adapter.onWebSocketText(
            """
                {"type": "start",
                 "id": "unconfiguredquery",
                 "payload": {"query": "query { q }"}}""".trimIndent()
        )
        socket.adapter.onWebSocketError(IllegalStateException("boom"))
        socket.onNext(
            OperationMessage(OperationType("unknown", Nothing::class), "invalidtype", null)
        )
        socket.onNext(
            OperationMessage(OperationType.GQL_START, "badpayload", null)
        )
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testConnectAckAndKeepAlive() {
        val testScheduler = TestScheduler()
        val socket = getSocket(keepAliveScheduler = testScheduler)
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
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "ka""""),
                    EasyMock.contains(""""id" : "connect"""")
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""payload" : "Already connected!""""),
                    EasyMock.and(
                        EasyMock.contains(""""type" : "connection_error""""),
                        EasyMock.contains(""""id" : "connect2"""")
                    )
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText("""{"type": "connection_init", "id": "connect", "payload": null}""")
        socket.adapter.onWebSocketText("""{"type": "connection_init", "id": "connect2", "payload": null}""")
        testScheduler.advanceTimeBy(config.keepAliveIntervalSeconds + 1, TimeUnit.SECONDS)
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
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText("""{"type": "connection_terminate", "id": "terminate", "payload":null}""")
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testWebSocketClose() {
        // use Schedulers.computation() since we're using the infinite subscription stream
        // and want to be able to call `onWebSocketClose` while the query is running
        val socket = getSocket(scheduler = Schedulers.computation())
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(mockRemote.sendString(EasyMock.anyString())).anyTimes()
        EasyMock.replay(mockRemote, mockSession)
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText("""{"type": "connection_init", "id": "connect", "payload":null}""")
        socket.adapter.onWebSocketText("""{"type": "start", "id": "run", "payload": {"query": "subscription {inf}"}}""")
        assertThat(socket.isDisposed).isFalse()
        socket.adapter.onWebSocketClose(StatusCode.NORMAL, "externally closed")
        assertThat(socket.adapter.session).isNull()
        assertThat(socket.isDisposed).isTrue()
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testStopQuery() {
        // and use Schedulers.computation() since we're using the infinite subscription stream
        val socket = getSocket(scheduler = Schedulers.computation())
        val mockRemote = EasyMock.mock<WebSocketRemoteEndpoint>(WebSocketRemoteEndpoint::class.java)
        val mockSession = EasyMock.mock<Session>(Session::class.java)
        // use these to signal the test that certain steps have been accomplished
        val data = CountDownLatch(1)
        val secondQueryErrored = CountDownLatch(1)
        val complete = CountDownLatch(1)
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
        ).andAnswer { data.countDown() }.atLeastOnce() // notify that data has been sent
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains("""already running"""),
                    EasyMock.and(
                        EasyMock.contains(""""type" : "error""""),
                        EasyMock.contains(""""id" : "longsubscription"""")
                    )
                )
            )
        ).andAnswer { secondQueryErrored.countDown() }.once()
        EasyMock.expect(
            mockRemote.sendString(
                EasyMock.and(
                    EasyMock.contains(""""type" : "complete""""),
                    EasyMock.contains(""""id" : "longsubscription"""")
                )
            )
        ).andAnswer { complete.countDown() }.once() // notify that complete has been sent
        EasyMock.replay(mockRemote, mockSession)
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText(
            """
            {"type": "start",
             "id": "longsubscription",
             "payload": {"query": "subscription { inf }"}}""".trimIndent()
        )

        data.await(1, TimeUnit.SECONDS)
        socket.adapter.onWebSocketText(
            """
            {"type": "start",
             "id": "longsubscription",
             "payload": {"query": "subscription { inf }"}}""".trimIndent()
        )
        secondQueryErrored.await(1, TimeUnit.SECONDS)

        socket.adapter.onWebSocketText(
            """
            {"type": "stop",
             "id": "longsubscription",
             "payload": null}""".trimIndent()
        )
        complete.await(1, TimeUnit.SECONDS)
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
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText(
            """
            {"type": "stop",
             "id": "unknownquery",
             "payload":null}""".trimIndent()
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
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText(
            """
            {"type": "start",
             "id": null,
             "payload": null}""".trimIndent()
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
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText("not json!")
        EasyMock.verify(mockRemote, mockSession)
    }
}
