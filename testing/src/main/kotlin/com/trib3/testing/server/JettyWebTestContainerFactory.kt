package com.trib3.testing.server

import org.eclipse.jetty.server.ServerConnector
import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.jetty.servlet.JettyWebContainerFactory
import org.glassfish.jersey.servlet.ServletContainer
import org.glassfish.jersey.test.DeploymentContext
import org.glassfish.jersey.test.spi.TestContainer
import org.glassfish.jersey.test.spi.TestContainerFactory
import java.net.URI
import javax.ws.rs.core.UriBuilder

/**
 * A Servlet-based [TestContainerFactory] for creating test container instances using jetty
 *
 * Equivalent of GrizzlyWebTestContainerFactory, but using jetty instead of grizzly.
 */
class JettyWebTestContainerFactory : TestContainerFactory {
    private class JettyWebTestContainer(private var uri: URI, context: DeploymentContext) : TestContainer {
        val server = JettyWebContainerFactory.create(
            UriBuilder.fromUri(uri).path(context.contextPath).build(),
            ServletContainer(context.resourceConfig),
            null,
            null
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
    }

    override fun create(baseUri: URI, deploymentContext: DeploymentContext): TestContainer {
        return JettyWebTestContainer(baseUri, deploymentContext)
    }
}
