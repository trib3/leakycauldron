package com.trib3.graphql.websocket

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.codahale.metrics.annotation.Timed
import com.trib3.json.ObjectMapperProvider
import com.trib3.testing.server.JettyWebTestContainerFactory
import com.trib3.testing.server.ResourceTestBase
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
import mu.KotlinLogging
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.api.exceptions.WebSocketTimeoutException
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.eclipse.jetty.websocket.core.Configuration
import org.eclipse.jetty.websocket.core.server.ServerUpgradeRequest
import org.eclipse.jetty.websocket.core.server.ServerUpgradeResponse
import org.eclipse.jetty.websocket.core.server.WebSocketCreator
import org.eclipse.jetty.websocket.core.server.WebSocketMappings
import org.eclipse.jetty.websocket.server.internal.JettyServerFrameHandlerFactory
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

    override fun getResource(): SimpleWebSocketResource {
        return rawResource
    }

    override fun getContainerFactory(): TestContainerFactory {
        return JettyWebTestContainerFactory()
    }

    /**
     * Tests that a websocket client can start a child coroutine, have the session
     * error due to idle timeout, and the main and child coroutines both get cancelled
     */
    @Test
    fun testTimeout() = runBlocking {
        val client = WebSocketClient()
        client.start()
        val session = client.connect(
            WebSocketAdapter(),
            resource.target("/websocket").uriBuilder.scheme("ws").queryParam("name", "timeout").build(),
        ).get()
        session.remote.sendString(
            mapper.writeValueAsString(
                OperationMessage(
                    OperationType.GQL_START,
                    "launch",
                    null,
                ),
            ),
        )
        Thread.sleep(1100) // sleep for longer than the timeout to make sure we get an exception
        val job = getResource().webSocketCreator.coroutines["name=timeout"]!!
        val childJob = getResource().webSocketCreator.coroutines["name=timeoutchild"]!!
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
    fun testClientSendsMessageAndDisconnects() = runBlocking {
        val client = WebSocketClient()
        client.start()
        val session = client.connect(
            WebSocketAdapter(),
            resource.target("/websocket").uriBuilder.scheme("ws").queryParam("name", "clientClose").build(),
        ).get()
        session.remote.sendString(mapper.writeValueAsString(OperationMessage(OperationType.GQL_START, "launch", null)))
        var maybeJob = getResource().webSocketCreator.coroutines["name=clientClosechild"]
        // ensure the start message gets processed and starts a child
        while (maybeJob == null) {
            delay(10)
            maybeJob = getResource().webSocketCreator.coroutines["name=clientClosechild"]
        }
        session.disconnect()
        val job = getResource().webSocketCreator.coroutines["name=clientClose"]!!
        val childJob = getResource().webSocketCreator.coroutines["name=clientClosechild"]!!
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
    fun testClientSendsMessagesAndCloses() = runBlocking {
        val client = WebSocketClient()
        client.start()
        val session = client.connect(
            WebSocketAdapter(),
            resource.target("/websocket").uriBuilder.scheme("ws").queryParam("name", "spin").build(),
        ).get()
        session.remote.sendString(mapper.writeValueAsString(OperationMessage(OperationType.GQL_START, "launch", null)))
        launch(Dispatchers.IO) {
            for (i in 0..19) {
                delay(100)
                log.info("SPIN: $i: $coroutineContext")
                session.remote.sendString(
                    mapper.writeValueAsString(
                        OperationMessage(
                            OperationType.GQL_START,
                            "ping",
                            null,
                        ),
                    ),
                )
            }
            session.close()
        }.join()
        val job = getResource().webSocketCreator.coroutines["name=spin"]!!
        val childJob = getResource().webSocketCreator.coroutines["name=spinchild"]!!
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
    fun testClientSendsMessagesAndFinishes() = runBlocking {
        val client = WebSocketClient()
        client.start()
        val session = client.connect(
            WebSocketAdapter(),
            resource.target("/websocket").uriBuilder.scheme("ws").queryParam("name", "finish").build(),
        ).get()
        session.remote.sendString(mapper.writeValueAsString(OperationMessage(OperationType.GQL_START, "launch", null)))
        launch(Dispatchers.IO) {
            for (i in 0..19) {
                delay(100)
                log.info("FINISH: $i: $coroutineContext")
                session.remote.sendString(
                    mapper.writeValueAsString(
                        OperationMessage(
                            OperationType.GQL_START,
                            "ping",
                            null,
                        ),
                    ),
                )
            }
            session.remote.sendString(
                mapper.writeValueAsString(
                    OperationMessage(
                        OperationType.GQL_START,
                        "finish",
                        null,
                    ),
                ),
            )
        }.join()
        val job = getResource().webSocketCreator.coroutines["name=finish"]!!
        val childJob = getResource().webSocketCreator.coroutines["name=finishchild"]!!
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
    override fun createWebSocket(req: ServerUpgradeRequest, resp: ServerUpgradeResponse): Any {
        val channel = Channel<OperationMessage<*>>()
        val adapter = object : GraphQLWebSocketAdapter(GraphQLWebSocketSubProtocol.APOLLO_PROTOCOL, channel, mapper) {
            override fun onWebSocketError(cause: Throwable) {
                errors[req.queryString] = cause
                super.onWebSocketError(cause)
            }
        }
        coroutines[req.queryString] = adapter.launch {
            for (msg in channel) {
                if (msg.id == "launch") {
                    coroutines["${req.queryString}child"] = launch {
                        while (true) {
                            delay(100)
                            log.info("CHILD: ${req.queryString}: ping: $coroutineContext")
                        }
                    }
                } else if (msg.id == "finish") {
                    coroutines["${req.queryString}child"]?.cancel()
                    break
                } else {
                    log.info("PARENT: ${req.queryString}: pong: ${msg.id}: $coroutineContext")
                }
            }
            log.info("DONE: ${req.queryString}: $coroutineContext")
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
    fun webSocketUpgrade(@Context request: HttpServletRequest, @Context response: HttpServletResponse): Response {
        val webSocketMapping = WebSocketMappings.getMappings(request.servletContext)
        val pathSpec = WebSocketMappings.parsePathSpec("/")
        if (webSocketMapping.getWebSocketCreator(pathSpec) == null) {
            webSocketMapping.addMapping(
                pathSpec,
                webSocketCreator,
                JettyServerFrameHandlerFactory.getFactory(request.servletContext),
                Configuration.ConfigurationCustomizer().apply {
                    this.idleTimeout = Duration.ofSeconds(1)
                },
            )
        }

        // Create a new WebSocketCreator for each request bound to an optional authorized principal
        if (webSocketMapping.upgrade(request, response, null)) {
            return Response.status(HttpStatus.SWITCHING_PROTOCOLS_101).build()
        }

        return Response.status(HttpStatus.METHOD_NOT_ALLOWED_405).build()
    }
}
