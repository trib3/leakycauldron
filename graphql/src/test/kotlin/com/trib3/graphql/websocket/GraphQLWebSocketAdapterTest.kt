package com.trib3.graphql.websocket

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.codahale.metrics.annotation.Timed
import com.trib3.graphql.execution.MessageGraphQLError
import com.trib3.graphql.resources.doWebSocketUpgrade
import com.trib3.json.ObjectMapperProvider
import com.trib3.testing.server.JettyWebTestContainerFactory
import com.trib3.testing.server.ResourceTestBase
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.exceptions.WebSocketTimeoutException
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.eclipse.jetty.websocket.core.Configuration
import org.eclipse.jetty.websocket.core.server.ServerUpgradeRequest
import org.eclipse.jetty.websocket.core.server.ServerUpgradeResponse
import org.eclipse.jetty.websocket.core.server.WebSocketCreator
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.testng.annotations.Test
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val mapper = ObjectMapperProvider().get()
private val log = KotlinLogging.logger {}

/**
 * Tests that the [GraphQLWebSocketAdapter] properly shuts down any coroutines
 * launched via its scope when the websocket closes and/or errors
 */
class GraphQLWebSocketAdapterTest : ResourceTestBase<SimpleWebSocketResource>() {
    val rawResource = SimpleWebSocketResource()

    override fun getResource(): SimpleWebSocketResource = rawResource

    override fun getContainerFactory(): TestContainerFactory = JettyWebTestContainerFactory()

    suspend fun getJob(name: String): Job {
        var maybeJob: Job? = null
        while (maybeJob == null) {
            maybeJob = getResource().webSocketCreator.coroutines[name]
            if (maybeJob == null) {
                delay(10)
            }
        }
        return maybeJob
    }

    @Test
    fun testNullSessionSendDoesntCrash() {
        val channel = Channel<OperationMessage<*>>()
        val adapter = GraphQLWebSocketAdapter(GraphQLWebSocketSubProtocol.APOLLO_PROTOCOL, channel, mapper)
        adapter.sendMessage(
            OperationMessage(
                OperationType.GQL_ERROR,
                "blah",
                listOf(MessageGraphQLError("Invalid message")),
            ),
        )
    }

    /**
     * Tests that a websocket client can start a child coroutine, have the session
     * error due to idle timeout, and the main and child coroutines both get cancelled
     */
    @Test
    fun testTimeout() =
        runBlocking {
            val client = WebSocketClient()
            client.start()
            val session =
                client
                    .connect(
                        object : Session.Listener.AutoDemanding {},
                        resource
                            .target("/websocket")
                            .uriBuilder
                            .scheme("ws")
                            .queryParam("name", "timeout")
                            .build(),
                    ).get()
            session.sendText(
                mapper.writeValueAsString(OperationMessage(OperationType.GQL_START, "launch", null)),
                null,
            )
            Thread.sleep(1100) // sleep for longer than the timeout to make sure we get an exception
            val job = getResource().webSocketCreator.coroutines["name=timeout"]!!
            val childJob = getJob("name=timeoutchild")
            job.join()
            val potentialError = getResource().webSocketCreator.errors["name=timeout"]!!
            assertThat(potentialError).isNotNull().isInstanceOf(WebSocketTimeoutException::class)
            for (j in listOf(job, childJob)) {
                assertThat(j.isActive).isFalse()
                assertThat(j.isCancelled).isTrue()
                assertThat(j.isCompleted).isTrue()
            }
        }

    /**
     * Tests that a websocket client can start a child coroutine, disconnect
     * the session, and the main and child coroutines both get cancelled
     */
    @Test
    fun testClientSendsMessageAndDisconnects() =
        runBlocking {
            val client = WebSocketClient()
            client.start()
            val session =
                client
                    .connect(
                        object : Session.Listener.AutoDemanding {},
                        resource
                            .target("/websocket")
                            .uriBuilder
                            .scheme("ws")
                            .queryParam("name", "clientClose")
                            .build(),
                    ).get()
            session.sendText(
                mapper.writeValueAsString(OperationMessage(OperationType.GQL_START, "launch", null)),
                null,
            )
            val job = getResource().webSocketCreator.coroutines["name=clientClose"]!!
            val childJob = getJob("name=clientClosechild")
            session.disconnect()
            job.join()
            for (j in listOf(job, childJob)) {
                assertThat(j.isActive).isFalse()
                assertThat(j.isCancelled).isTrue()
                assertThat(j.isCompleted).isTrue()
            }
        }

    /**
     * Tests that a websocket client can start a child coroutine, have the session
     * last longer than the idle timeout, and the main and child coroutines both get
     * cancelled when the client closes the session.
     */
    @Test
    fun testClientSendsMessagesAndCloses() =
        runBlocking {
            val client = WebSocketClient()
            client.start()
            val session =
                client
                    .connect(
                        object : Session.Listener.AutoDemanding {},
                        resource
                            .target("/websocket")
                            .uriBuilder
                            .scheme("ws")
                            .queryParam("name", "spin")
                            .build(),
                    ).get()
            session.sendText(
                mapper.writeValueAsString(OperationMessage(OperationType.GQL_START, "launch", null)),
                null,
            )
            launch(Dispatchers.IO) {
                for (i in 0..19) {
                    delay(100)
                    log.info { "SPIN: $i: $coroutineContext" }
                    session.sendText(
                        mapper.writeValueAsString(
                            OperationMessage(
                                OperationType.GQL_START,
                                "ping",
                                null,
                            ),
                        ),
                        null,
                    )
                }
                session.close()
            }.join()
            val job = getResource().webSocketCreator.coroutines["name=spin"]!!
            val childJob = getJob("name=spinchild")
            job.join()
            assertThat(getResource().webSocketCreator.errors["name=spin"]).isNull()
            for (j in listOf(job, childJob)) {
                assertThat(j.isActive).isFalse()
                assertThat(j.isCancelled).isTrue()
                assertThat(j.isCompleted).isTrue()
            }
        }

    /**
     * Tests that a websocket client can start a child coroutine, have the session
     * last longer than the idle timeout, and the main and child coroutines both get
     * cancelled when the server closes the session.
     */
    @Test
    fun testClientSendsMessagesAndFinishes() =
        runBlocking {
            val client = WebSocketClient()
            client.start()
            val session =
                client
                    .connect(
                        object : Session.Listener.AutoDemanding {},
                        resource
                            .target("/websocket")
                            .uriBuilder
                            .scheme("ws")
                            .queryParam("name", "finish")
                            .build(),
                    ).get()
            session.sendText(
                mapper.writeValueAsString(OperationMessage(OperationType.GQL_START, "launch", null)),
                null,
            )
            launch(Dispatchers.IO) {
                for (i in 0..19) {
                    delay(100)
                    log.info { "FINISH: $i: $coroutineContext" }
                    session.sendText(
                        mapper.writeValueAsString(
                            OperationMessage(
                                OperationType.GQL_START,
                                "ping",
                                null,
                            ),
                        ),
                        null,
                    )
                }
                session.sendText(
                    mapper.writeValueAsString(
                        OperationMessage(
                            OperationType.GQL_START,
                            "finish",
                            null,
                        ),
                    ),
                    null,
                )
            }.join()
            val job = getResource().webSocketCreator.coroutines["name=finish"]!!
            val childJob = getJob("name=finishchild")
            job.join()
            for (j in listOf(job, childJob)) {
                assertThat(j.isActive).isFalse()
                assertThat(j.isCompleted).isTrue()
            }
            assertThat(childJob.isCancelled).isTrue()
            assertThat(job.isCancelled).isFalse()
            assertThat(getResource().webSocketCreator.errors["name=finish"]).isNull()
        }
}

/**
 * [WebSocketCreator] implementation that launches coroutines to
 * process messages from a [GraphQLWebSocketAdapter], and tracks
 * the coroutines and any errors that happen on the session.
 */
class SessionTrackingCreator : WebSocketCreator {
    val coroutines = ConcurrentHashMap<String, Job>()
    val errors = ConcurrentHashMap<String, Throwable>()

    override fun createWebSocket(
        req: ServerUpgradeRequest,
        resp: ServerUpgradeResponse,
        callback: Callback,
    ): Any {
        val channel = Channel<OperationMessage<*>>()
        val adapter =
            object : GraphQLWebSocketAdapter(GraphQLWebSocketSubProtocol.APOLLO_PROTOCOL, channel, mapper) {
                override fun onWebSocketError(cause: Throwable) {
                    errors[req.httpURI.query] = cause
                    super.onWebSocketError(cause)
                }
            }
        coroutines[req.httpURI.query] =
            adapter.launch {
                for (msg in channel) {
                    if (msg.id == "launch") {
                        coroutines["${req.httpURI.query}child"] =
                            launch {
                                while (true) {
                                    delay(100)
                                    log.info { "CHILD: ${req.httpURI.query}: ping: $coroutineContext" }
                                }
                            }
                    } else if (msg.id == "finish") {
                        coroutines["${req.httpURI.query}child"]?.cancel()
                        break
                    } else {
                        log.info { "PARENT: ${req.httpURI.query}: pong: ${msg.id}: $coroutineContext" }
                    }
                }
                log.info { "DONE: ${req.httpURI.query}: $coroutineContext" }
            }
        return adapter
    }
}

/**
 * Resource that does a websocket upgrade with an idle timeout of 1 second on the socket
 */
@Path("/")
class SimpleWebSocketResource {
    val webSocketCreator = SessionTrackingCreator()

    @GET
    @Path("/websocket")
    @Timed
    fun webSocketUpgrade(
        @Context request: HttpServletRequest,
        @Context response: HttpServletResponse,
    ): Response =
        doWebSocketUpgrade(
            request,
            response,
            webSocketCreator,
            Configuration.ConfigurationCustomizer().apply {
                this.idleTimeout = Duration.ofSeconds(1)
            },
        )
}
