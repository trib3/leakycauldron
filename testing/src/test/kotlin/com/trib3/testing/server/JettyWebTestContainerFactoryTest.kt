package com.trib3.testing.server

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.messageContains
import org.eclipse.jetty.server.NetworkConnector
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.test.DeploymentContext
import org.testng.annotations.Test
import java.net.URI

/**
 * Actual functionality mostly tested in [ResourceTestBaseJettyWebContainerTest] integration test,
 * but hit some edge cases here for fuller coverage
 */
class JettyWebTestContainerFactoryTest {
    val deploymentContext = DeploymentContext.newInstance(ResourceConfig())
    val factory = JettyWebTestContainerFactory()

    @Test
    fun testNoUriPath() {
        assertThat {
            factory.create(
                URI("http", "test.com", "", ""),
                deploymentContext,
            )
        }.isFailure().all {
            messageContains("URI path")
            messageContains("must be present")
        }
    }

    @Test
    fun testNoSlashInUriPath() {
        assertThat {
            factory.create(
                URI(null, null, "zzz", ""),
                deploymentContext,
            )
        }.isFailure().all {
            messageContains("URI path")
            messageContains("must start with a '/'")
        }
    }

    @Test
    fun testWrongUriScheme() {
        assertThat {
            factory.create(
                URI("https", "test.com", "/zzz", ""),
                deploymentContext,
            )
        }.isFailure().all {
            messageContains("URI scheme")
            messageContains("must be http")
        }
    }

    @Test
    fun testDefaultPort() {
        val server = factory.create(
            URI("http", "test.com", "/zzz", ""),
            deploymentContext,
        )
        server.stop()
        val port = (server.server.connectors.firstOrNull() as? NetworkConnector)?.port
        assertThat(port).isEqualTo(9080)
    }
}
