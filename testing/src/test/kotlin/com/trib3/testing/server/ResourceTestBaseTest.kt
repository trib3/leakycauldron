package com.trib3.testing.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletContextRequest
import org.eclipse.jetty.ee10.websocket.server.internal.JettyServerFrameHandlerFactory
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.util.FutureCallback
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.eclipse.jetty.websocket.core.Configuration
import org.eclipse.jetty.websocket.core.WebSocketConstants
import org.eclipse.jetty.websocket.core.server.WebSocketCreator
import org.eclipse.jetty.websocket.core.server.WebSocketMappings
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.testng.annotations.Test
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val webSocketCreator =
    WebSocketCreator { _, _, _ ->
        object : Session.Listener.AutoDemanding {
            var session: Session? = null

            override fun onWebSocketOpen(session: Session?) {
                this.session = session
            }

            override fun onWebSocketText(message: String) {
                session?.sendText(message, null)
            }
        }
    }

@Path("/")
class SimpleResource {
    @GET
    fun getThing(
        @Context request: HttpServletRequest,
    ): String = request.getHeader("Test-Header")

    @GET
    @Path("/websocket")
    fun webSocketUpgrade(
        @Context request: HttpServletRequest,
        @Context response: HttpServletResponse,
    ): Response {
        val ctxHandler = ServletContextHandler.getServletContextHandler(request.servletContext)
        val webSocketMapping = WebSocketMappings.getMappings(ctxHandler)
        val pathSpec = WebSocketMappings.parsePathSpec("/")
        if (webSocketMapping.getWebSocketCreator(pathSpec) == null) {
            synchronized(webSocketMapping) {
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
            }
        }
        val wsrequest = ServletContextRequest.getServletContextRequest(request)
        if (webSocketMapping.handshaker.isWebSocketUpgradeRequest(wsrequest)) {
            val wsresponse = wsrequest.servletContextResponse
            val callback = FutureCallback()
            try {
                wsrequest.setAttribute(WebSocketConstants.WEBSOCKET_WRAPPED_REQUEST_ATTRIBUTE, request)
                wsrequest.setAttribute(WebSocketConstants.WEBSOCKET_WRAPPED_RESPONSE_ATTRIBUTE, response)
                if (webSocketMapping.upgrade(wsrequest, wsresponse, callback, null)) {
                    callback.block()
                    return Response.status(HttpStatus.SWITCHING_PROTOCOLS_101).build()
                }
            } finally {
                wsrequest.removeAttribute(WebSocketConstants.WEBSOCKET_WRAPPED_REQUEST_ATTRIBUTE)
                wsrequest.removeAttribute(WebSocketConstants.WEBSOCKET_WRAPPED_RESPONSE_ATTRIBUTE)
            }
        }

        return Response.status(HttpStatus.METHOD_NOT_ALLOWED_405).build()
    }
}

class ResourceTestBaseJettyWebContainerTest : ResourceTestBase<SimpleResource>() {
    override fun getResource(): SimpleResource = SimpleResource()

    @Test
    fun testSimpleResource() {
        val response =
            resource
                .target("/")
                .request()
                .header("Test-Header", "Test-Value")
                .get()
        assertThat(response.status).isEqualTo(200)
        assertThat(response.readEntity(String::class.java)).isEqualTo("Test-Value")
    }

    @Test
    fun testSimpleWebSocket() {
        val client = WebSocketClient()
        client.start()
        val lock = ReentrantLock()
        val condition = lock.newCondition()
        var responseText: String? = null
        val clientAdapter =
            object : Session.Listener.AutoDemanding {
                override fun onWebSocketText(message: String) {
                    lock.withLock {
                        responseText = message
                        condition.signal()
                    }
                }
            }
        val session =
            client
                .connect(
                    clientAdapter,
                    ClientUpgradeRequest(
                        resource
                            .target("/websocket")
                            .uriBuilder
                            .scheme("ws")
                            .build(),
                    ),
                ).get()
        lock.withLock {
            session.sendText("ping", null)
            condition.await()
        }
        assertThat(responseText).isNotNull().isEqualTo("ping")
    }
}

class ResourceTestBaseInMemoryContainerTest : ResourceTestBase<SimpleResource>() {
    override fun getResource(): SimpleResource = SimpleResource()

    override fun getContainerFactory(): TestContainerFactory = InMemoryTestContainerFactory()

    @Test
    fun testSimpleResource() {
        val response =
            resource
                .target("/")
                .request()
                .header("Test-Header", "Test-Value")
                .get()
        assertThat(response.status).isEqualTo(500)
    }
}
