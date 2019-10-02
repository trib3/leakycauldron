package com.trib3.graphql.websocket

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.expediagroup.graphql.SchemaGeneratorConfig
import com.expediagroup.graphql.TopLevelObject
import com.expediagroup.graphql.toSchema
import com.trib3.config.ConfigLoader
import com.trib3.config.KMSStringSelectReader
import com.trib3.graphql.GraphQLConfig
import com.trib3.json.ObjectMapperProvider
import com.trib3.testing.LeakyMock
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
                if (value > 1) {
                    // add a delay so we don't flood the queue with too many messages before
                    // a stop command can be processed in a reasonable amount of time
                    Thread.sleep(20)
                }
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""q" : [ "1", "2", "3" ]"""),
                    LeakyMock.and(
                        LeakyMock.contains(""""type" : "data""""),
                        LeakyMock.contains(""""id" : "simplequery"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "complete""""),
                    LeakyMock.contains(""""id" : "simplequery"""")
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""v" : [ "1", "2", "3" ]"""),
                    LeakyMock.and(
                        LeakyMock.contains(""""type" : "data""""),
                        LeakyMock.contains(""""id" : "simplequery"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "complete""""),
                    LeakyMock.contains(""""id" : "simplequery"""")
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        val errorCapture = EasyMock.newCapture<String>()
        EasyMock.expect(mockRemote.sendString(EasyMock.capture(errorCapture))).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "complete""""),
                    LeakyMock.contains(""""id" : "errorquery"""")
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
        val mockGraphQL = LeakyMock.mock<GraphQL>()
        val socket = getSocket(mockGraphQL)
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(mockGraphQL.execute(LeakyMock.anyObject<ExecutionInput>()))
            .andThrow(IllegalStateException("ExecutionError"))
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "error""""),
                    LeakyMock.contains(""""id" : "executionerror"""")
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""s" : "1""""),
                    LeakyMock.and(
                        LeakyMock.contains(""""type" : "data""""),
                        LeakyMock.contains(""""id" : "simplesubscription"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""s" : "2""""),
                    LeakyMock.and(
                        LeakyMock.contains(""""type" : "data""""),
                        LeakyMock.contains(""""id" : "simplesubscription"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""s" : "3""""),
                    LeakyMock.and(
                        LeakyMock.contains(""""type" : "data""""),
                        LeakyMock.contains(""""id" : "simplesubscription"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "complete""""),
                    LeakyMock.contains(""""id" : "simplesubscription"""")
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""e" : "1""""),
                    LeakyMock.and(
                        LeakyMock.contains(""""type" : "data""""),
                        LeakyMock.contains(""""id" : "errorsubscription"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""e" : "2""""),
                    LeakyMock.and(
                        LeakyMock.contains(""""type" : "data""""),
                        LeakyMock.contains(""""id" : "errorsubscription"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "error""""),
                    LeakyMock.contains(""""id" : "errorsubscription"""")
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "error""""),
                    LeakyMock.contains(""""id" : "unknownoperation"""")
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "error""""),
                    LeakyMock.and(
                        LeakyMock.contains(""""id" : "unconfiguredquery""""),
                        LeakyMock.contains("not configured")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "error""""),
                    LeakyMock.and(
                        LeakyMock.contains(""""id" : "invalidtype""""),
                        LeakyMock.contains(""""payload" : "Unknown message type"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "error""""),
                    LeakyMock.and(
                        LeakyMock.contains(""""id" : "badpayload""""),
                        LeakyMock.contains(""""payload" : "Invalid payload for query"""")
                    )
                )
            )
        ).once()
        EasyMock.expect(mockSession.close(EasyMock.eq(StatusCode.SERVER_ERROR), LeakyMock.contains("boom"))).once()
        EasyMock.expect(mockSession.close(EasyMock.anyInt(), LeakyMock.anyString())).anyTimes()

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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "connection_ack""""),
                    LeakyMock.contains(""""id" : "connect"""")
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "ka""""),
                    LeakyMock.contains(""""id" : "connect"""")
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "ka""""),
                    LeakyMock.contains(""""id" : "connect"""")
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""payload" : "Already connected!""""),
                    LeakyMock.and(
                        LeakyMock.contains(""""type" : "connection_error""""),
                        LeakyMock.contains(""""id" : "connect2"""")
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(mockRemote.sendString(LeakyMock.anyString())).anyTimes()
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        // use these to signal the test that certain steps have been accomplished
        val data = CountDownLatch(1)
        val secondQueryErrored = CountDownLatch(1)
        val complete = CountDownLatch(1)
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""inf" : """"),
                    LeakyMock.and(
                        LeakyMock.contains(""""type" : "data""""),
                        LeakyMock.contains(""""id" : "longsubscription"""")
                    )
                )
            )
        ).andAnswer { data.countDown() }.atLeastOnce() // notify that data has been sent
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains("""already running"""),
                    LeakyMock.and(
                        LeakyMock.contains(""""type" : "error""""),
                        LeakyMock.contains(""""id" : "longsubscription"""")
                    )
                )
            )
        ).andAnswer { secondQueryErrored.countDown() }.once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "complete""""),
                    LeakyMock.contains(""""id" : "longsubscription"""")
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "error""""),
                    LeakyMock.contains(""""id" : "unknownquery"""")
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "error""""),
                    LeakyMock.contains(""""payload" : "Must pass a message id""")
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
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "error""""),
                    LeakyMock.contains(""""payload" : "Unrecognized token""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText("not json!")
        EasyMock.verify(mockRemote, mockSession)
    }
}
