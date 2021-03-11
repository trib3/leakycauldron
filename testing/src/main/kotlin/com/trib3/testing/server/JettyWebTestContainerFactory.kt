package com.trib3.testing.server

import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.eclipse.jetty.webapp.Configuration
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.webapp.WebXmlConfiguration
import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.internal.guava.ThreadFactoryBuilder
import org.glassfish.jersey.jetty.internal.LocalizationMessages
import org.glassfish.jersey.process.JerseyProcessingUncaughtExceptionHandler
import org.glassfish.jersey.server.spi.Container
import org.glassfish.jersey.servlet.ServletContainer
import org.glassfish.jersey.test.DeploymentContext
import org.glassfish.jersey.test.spi.TestContainer
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.glassfish.jersey.uri.UriComponent
import java.net.URI
import java.util.Locale
import javax.servlet.Servlet
import javax.ws.rs.core.UriBuilder

/**
 * A Servlet-based [TestContainerFactory] for creating test container instances using jetty
 *
 * Equivalent of GrizzlyWebTestContainerFactory, but using jetty instead of grizzly.
 */
class JettyWebTestContainerFactory : TestContainerFactory {

    // Same as JettyHttpContainerFactory#JettyConnectorThreadPool, but with newThread as public
    private class JettyConnectorThreadPool : QueuedThreadPool() {
        private val threadFactory = ThreadFactoryBuilder()
            .setNameFormat("jetty-http-server-%d")
            .setUncaughtExceptionHandler(JerseyProcessingUncaughtExceptionHandler())
            .build()

        override fun newThread(runnable: Runnable): Thread {
            return threadFactory.newThread(runnable)
        }
    }

    private class JettyWebTestContainer(private var uri: URI, context: DeploymentContext) : TestContainer {
        val server = create(
            UriBuilder.fromUri(uri).path(context.contextPath).build(),
            ServletContainer(context.resourceConfig)
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
            servlet: Servlet
        ): Server {
            requireNotNull(uri.path) { "The URI path, of the URI $uri, must be non-null" }
            require(uri.path.isNotEmpty()) { "The URI path, of the URI $uri, must be present" }
            require(uri.path[0] == '/') { "The URI path, of the URI $uri. must start with a '/'" }

            val path = String.format(Locale.US, "/%s", UriComponent.decodePath(uri.path, true)[1].toString())
            val context = WebAppContext()
            context.displayName = "JettyContext"
            context.contextPath = path
            context.configurations = arrayOf<Configuration>(WebXmlConfiguration())
            val holder = ServletHolder(servlet)
            context.addServlet(holder, "/*")

            val server = createServer(uri)
            server.handler = context
            server.start()
            return server
        }

        // simplified version of JettyHttpContainerFactory#createServer
        private fun createServer(
            uri: URI
        ): Server {
            require("http".equals(uri.scheme)) { LocalizationMessages.WRONG_SCHEME_WHEN_USING_HTTP() }
            val defaultPort = Container.DEFAULT_HTTP_PORT
            val port = if (uri.port == -1) defaultPort else uri.port

            val server = Server(JettyConnectorThreadPool())
            val config = HttpConfiguration()
            val http = ServerConnector(server, HttpConnectionFactory(config))
            http.port = port
            server.connectors = arrayOf<Connector>(http)
            return server
        }
    }

    override fun create(baseUri: URI, deploymentContext: DeploymentContext): TestContainer {
        return JettyWebTestContainer(baseUri, deploymentContext)
    }
}
