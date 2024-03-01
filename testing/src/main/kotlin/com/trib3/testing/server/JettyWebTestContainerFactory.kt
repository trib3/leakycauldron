package com.trib3.testing.server

import jakarta.servlet.Servlet
import jakarta.ws.rs.core.UriBuilder
import org.eclipse.jetty.annotations.AnnotationConfiguration
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.eclipse.jetty.webapp.Configuration
import org.eclipse.jetty.webapp.WebAppContext
import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.servlet.ServletContainer
import org.glassfish.jersey.test.DeploymentContext
import org.glassfish.jersey.test.spi.TestContainer
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.glassfish.jersey.uri.UriComponent
import java.net.URI
import java.util.Locale

private const val DEFAULT_PORT = 9080

/**
 * A Servlet-based [TestContainerFactory] for creating test container instances using jetty with WebSocket support.
 *
 * Equivalent of GrizzlyWebTestContainerFactory, but using jetty instead of grizzly.
 */
class JettyWebTestContainerFactory : TestContainerFactory {
    class JettyWebTestContainer(private var uri: URI, context: DeploymentContext) : TestContainer {
        val server =
            create(
                UriBuilder.fromUri(uri).path(context.contextPath).build(),
                ServletContainer(context.resourceConfig),
            )

        override fun getClientConfig(): ClientConfig? {
            return null
        }

        override fun start() {
            server.start()
            uri = UriBuilder.fromUri(uri).port((server.connectors.first() as ServerConnector).localPort).build()
        }

        override fun stop() {
            server.stop()
        }

        override fun getBaseUri(): URI {
            return uri
        }

        // simplified version of JettyWebContainerFactory#create
        private fun create(
            uri: URI,
            servlet: Servlet,
        ): Server {
            require(uri.path.isNotBlank()) { "The URI path, of the URI $uri, must be present" }
            require(uri.path[0] == '/') { "The URI path, of the URI $uri. must start with a '/'" }

            val path = String.format(Locale.US, "/%s", UriComponent.decodePath(uri.path, true)[1].toString())
            val context = WebAppContext()
            context.displayName = "JettyContext"
            context.contextPath = path
            // for running tests we just want a straightforward classloader that doesn't hide things
            // like the WebAppClassLoader does, so websockets get detected properly if on the classpath
            context.classLoader = this::class.java.classLoader
            context.isParentLoaderPriority
            context.setConfigurations(arrayOf<Configuration>(AnnotationConfiguration()))
            val holder = ServletHolder(servlet)
            context.addServlet(holder, "/*")
            val server = createServer(uri)
            server.handler = context
            server.start()
            return server
        }

        // simplified version of JettyHttpContainerFactory#createServer
        private fun createServer(uri: URI): Server {
            require("http" == uri.scheme) { "The URI scheme, of the URI $uri, must be http" }
            val port =
                if (uri.port == -1) {
                    DEFAULT_PORT
                } else {
                    uri.port
                }

            val server = Server(QueuedThreadPool())
            val config = HttpConfiguration()
            val http = ServerConnector(server, HttpConnectionFactory(config))
            http.port = port
            server.connectors = arrayOf<Connector>(http)
            return server
        }
    }

    override fun create(
        baseUri: URI,
        deploymentContext: DeploymentContext,
    ): JettyWebTestContainer {
        return JettyWebTestContainer(baseUri, deploymentContext)
    }
}
