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
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.eclipse.jetty.websocket.core.Configuration
import org.eclipse.jetty.websocket.core.server.WebSocketCreator
import org.eclipse.jetty.websocket.core.server.WebSocketMappings
import org.eclipse.jetty.websocket.server.internal.JettyServerFrameHandlerFactory
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.testng.annotations.Test
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val webSocketCreator = WebSocketCreator { _, _ ->
    object : WebSocketAdapter() {
        override fun onWebSocketText(message: String) {
            remote.sendString(message)
        }
    }
}

@Path("/")
class SimpleResource {
    @GET
    fun getThing(@Context request: HttpServletRequest): String {
        return request.getHeader("Test-Header")
    }

    @GET
    @Path("/websocket")
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

class ResourceTestBaseJettyWebContainerTest : ResourceTestBase<SimpleResource>() {
    override fun getResource(): SimpleResource {
        return SimpleResource()
    }

    @Test
    fun testSimpleResource() {
        val response = resource.target("/").request().header("Test-Header", "Test-Value").get()
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
        val clientAdapter = object : WebSocketAdapter() {
            override fun onWebSocketText(message: String) {
                lock.withLock {
                    responseText = message
                    condition.signal()
                }
            }
        }
        val session =
            client.connect(
                clientAdapter,
                resource.target("/websocket").uriBuilder.scheme("ws").build(),
                ClientUpgradeRequest(),
            )
                .get()
        lock.withLock {
            session.remote.sendString("ping")
            condition.await()
        }
        assertThat(responseText).isNotNull().isEqualTo("ping")
    }
}

class ResourceTestBaseInMemoryContainerTest : ResourceTestBase<SimpleResource>() {
    override fun getResource(): SimpleResource {
        return SimpleResource()
    }

    override fun getContainerFactory(): TestContainerFactory {
        return InMemoryTestContainerFactory()
    }

    @Test
    fun testSimpleResource() {
        val response = resource.target("/").request().header("Test-Header", "Test-Value").get()
        assertThat(response.status).isEqualTo(500)
    }
}
