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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.websocket.api.CloseException
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.eclipse.jetty.websocket.server.WebSocketServerFactory
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.testng.annotations.Test
import java.util.concurrent.ConcurrentHashMap
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response

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
            resource.target("/websocket").uriBuilder.scheme("ws").queryParam("name", "timeout").build()
        ).get()
        session.remote.sendString(
            mapper.writeValueAsString(
                OperationMessage(
                    OperationType.GQL_START,
                    "launch",
                    null
                )
            )
        )
        Thread.sleep(1100) // sleep for longer than the timeout to make sure we get an exception
        val job = getResource().webSocketCreator.coroutines["name=timeout"]!!
        val childJob = getResource().webSocketCreator.coroutines["name=timeoutchild"]!!
        job.join()
        val potentialError = getResource().webSocketCreator.errors["name=timeout"]!!
        assertThat(potentialError).isNotNull().isInstanceOf(CloseException::class)
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
            resource.target("/websocket").uriBuilder.scheme("ws").queryParam("name", "clientClose").build()
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
        assertThat(getResource().webSocketCreator.errors["name=clientClose"]).isNull()
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
            resource.target("/websocket").uriBuilder.scheme("ws").queryParam("name", "spin").build()
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
                            null
                        )
                    )
                )
            }
            session.close()
        }.join()
        val job = getResource().webSocketCreator.coroutines["name=spin"]!!
        val childJob = getResource().webSocketCreator.coroutines["name=spinchild"]!!
        job.join()
        assertThat(getResource().webSocketCreator.errors["name=clientClose"]).isNull()
        for (j in listOf(job, childJob)) {
            assertThat(j.isActive).isFalse()
            assertThat(j.isCancelled).isTrue()
            assertThat(j.isCompleted).isTrue()
        }
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
    override fun createWebSocket(req: ServletUpgradeRequest, resp: ServletUpgradeResponse): Any {
        val channel = Channel<OperationMessage<*>>()
        val adapter = object : GraphQLWebSocketAdapter(channel, mapper) {
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

    private val webSocketFactory = WebSocketServerFactory().apply {
        this.policy.idleTimeout = 1000 // timeout after 1 second to cause a WebSocketError
        this.creator = webSocketCreator
        this.start()
    }

    @GET
    @Path("/websocket")
    @Timed
    fun webSocketUpgrade(@Context request: HttpServletRequest, @Context response: HttpServletResponse): Response {
        if (webSocketFactory.isUpgradeRequest(request, response)) {
            if (webSocketFactory.acceptWebSocket(request, response)) {
                return Response.status(HttpStatus.SWITCHING_PROTOCOLS_101).build()
            }
        }
        return Response.status(HttpStatus.METHOD_NOT_ALLOWED_405).build()
    }
}
