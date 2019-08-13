package com.trib3.graphql

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.trib3.config.ConfigLoader
import com.trib3.config.KMSStringSelectReader
import org.testng.annotations.Test

class GraphQLConfigTest {
    companion object {
        val DEFAULT_KEEPALIVE = 15L
    }

    @Test
    fun testConfig() {
        val config = GraphQLConfig(ConfigLoader(KMSStringSelectReader(null)))
        assertThat(config.keepAliveIntervalSeconds).isEqualTo(DEFAULT_KEEPALIVE)
        assertThat(config.webSocketSubProtocol).isEqualTo("graphql-ws")
    }
}
