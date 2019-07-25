package com.trib3.server.graphql

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.trib3.config.ConfigLoader
import com.trib3.config.KMSStringSelectReader
import org.testng.annotations.Test

class GraphQLConfigTest {
    @Test
    fun testConfig() {
        val config = GraphQLConfig(ConfigLoader(KMSStringSelectReader(null)))
        assertThat(config.keepAliveIntervalSeconds).isEqualTo(15L)
        assertThat(config.webSocketSubProtocol).isEqualTo("graphql-ws")
    }
}
