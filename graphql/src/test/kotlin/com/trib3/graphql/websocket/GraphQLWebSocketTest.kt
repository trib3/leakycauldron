package com.trib3.graphql.websocket

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
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
        return flowOf("1", "2", "3").asPublisher()
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
        }.asFlow().asPublisher()
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
        }.asFlow().asPublisher()
    }
}

@UseExperimental(ExperimentalCoroutinesApi::class)
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
     * default to using the "Unconfined" (ie, current thread) scheduler
     * so that we don't have to deal with multiple threads unless necessary
     */
    private fun getSocket(
        graphQL: GraphQL = testGraphQL,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        keepAliveDispatcher: CoroutineDispatcher = Dispatchers.Default
    ): GraphQLWebSocketConsumer {
        val channel = Channel<OperationMessage<*>>()
        val adapter = GraphQLWebSocketAdapter(channel, mapper, dispatcher)
        val consumer = GraphQLWebSocketConsumer(
            graphQL,
            config,
            channel,
            adapter,
            keepAliveDispatcher
        )
        adapter.launch {
            consumer.consume(this)
        }
        return consumer
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
                    LeakyMock.contains(""""type" : "data""""),
                    LeakyMock.contains(""""id" : "simplequery"""")
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
                    LeakyMock.contains(""""type" : "data""""),
                    LeakyMock.contains(""""id" : "simplequery"""")
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
                    LeakyMock.contains(""""type" : "data""""),
                    LeakyMock.contains(""""id" : "simplesubscription""""),
                    LeakyMock.contains(""""RequestId" : "simplesubscription"""")
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""s" : "2""""),
                    LeakyMock.contains(""""type" : "data""""),
                    LeakyMock.contains(""""id" : "simplesubscription""""),
                    LeakyMock.contains(""""RequestId" : "simplesubscription"""")
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""s" : "3""""),
                    LeakyMock.contains(""""type" : "data""""),
                    LeakyMock.contains(""""id" : "simplesubscription""""),
                    LeakyMock.contains(""""RequestId" : "simplesubscription"""")
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
                    LeakyMock.contains(""""type" : "data""""),
                    LeakyMock.contains(""""id" : "errorsubscription"""")
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""e" : "2""""),
                    LeakyMock.contains(""""type" : "data""""),
                    LeakyMock.contains(""""id" : "errorsubscription"""")
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
        val socket = getSocket()
        assertThat(socket.graphQL).isNotNull()
        assertThat(socket.keepAliveDispatcher).isEqualTo(Dispatchers.Default)
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
                    LeakyMock.contains(""""id" : "invalidtype""""),
                    LeakyMock.contains(""""payload" : "Unknown message type"""")
                )
            )
        ).once()
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains(""""type" : "error""""),
                    LeakyMock.contains(""""id" : "badpayload""""),
                    LeakyMock.contains(""""payload" : "Invalid payload for query"""")
                )
            )
        ).once()
        EasyMock.expect(mockSession.close(EasyMock.anyInt(), LeakyMock.anyString())).anyTimes()

        EasyMock.replay(mockRemote, mockSession)

        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText(
            """
            {"type": "unknown",
             "id": "unknownoperation",
             "payload": null}""".trimIndent()
        )
        runBlocking {
            socket.handleMessage(
                OperationMessage(OperationType("unknown", Nothing::class), "invalidtype", null),
                this
            )
            socket.handleMessage(
                OperationMessage(OperationType.GQL_START, "badpayload", null),
                this
            )
        }
        socket.adapter.onWebSocketError(IllegalStateException("boom"))
        socket.adapter.onWebSocketClose(StatusCode.SERVER_ERROR, "boom")
        assertThat(socket.channel.isClosedForReceive).isTrue()
        assertThat(socket.channel.isClosedForSend).isTrue()
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testConnectAckAndKeepAlive() {
        val testDispatcher = TestCoroutineDispatcher()
        val socket = getSocket(keepAliveDispatcher = testDispatcher)
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
                    LeakyMock.contains(""""type" : "connection_error""""),
                    LeakyMock.contains(""""id" : "connect2"""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText("""{"type": "connection_init", "id": "connect", "payload": null}""")
        socket.adapter.onWebSocketText("""{"type": "connection_init", "id": "connect2", "payload": null}""")
        testDispatcher.advanceTimeBy((config.keepAliveIntervalSeconds + 1) * 1000)
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
        // use Dispatchers.Default since we're using the infinite subscription stream
        // and want to be able to call `onWebSocketClose` while the query is running
        val socket = getSocket(dispatcher = Dispatchers.Default)
        val mockRemote = LeakyMock.mock<WebSocketRemoteEndpoint>()
        val mockSession = LeakyMock.mock<Session>()
        EasyMock.expect(mockSession.remote).andReturn(mockRemote).anyTimes()
        EasyMock.expect(mockRemote.sendString(LeakyMock.anyString())).anyTimes()
        EasyMock.replay(mockRemote, mockSession)
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText("""{"type": "connection_init", "id": "connect", "payload":null}""")
        socket.adapter.onWebSocketText("""{"type": "start", "id": "run", "payload": {"query": "subscription {inf}"}}""")
        assertThat(socket.channel.isClosedForSend).isFalse()
        socket.adapter.onWebSocketClose(StatusCode.NORMAL, "externally closed")
        assertThat(socket.adapter.session).isNull()
        assertThat(socket.channel.isClosedForSend).isTrue()
        EasyMock.verify(mockRemote, mockSession)
    }

    @Test
    fun testStopQuery() {
        // and use Dispatchers.Default since we're using the infinite subscription stream
        val socket = getSocket(dispatcher = Dispatchers.Default)
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
                    LeakyMock.contains(""""type" : "data""""),
                    LeakyMock.contains(""""id" : "longsubscription"""")
                )
            )
        ).andAnswer { data.countDown() }.atLeastOnce() // notify that data has been sent
        EasyMock.expect(
            mockRemote.sendString(
                LeakyMock.and(
                    LeakyMock.contains("""already running"""),
                    LeakyMock.contains(""""type" : "error""""),
                    LeakyMock.contains(""""id" : "longsubscription"""")
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
                    LeakyMock.contains(""""payload" : "Invalid message""")
                )
            )
        ).once()

        EasyMock.replay(mockRemote, mockSession)
        socket.adapter.onWebSocketConnect(mockSession)
        socket.adapter.onWebSocketText("not json!")
        EasyMock.verify(mockRemote, mockSession)
    }
}
