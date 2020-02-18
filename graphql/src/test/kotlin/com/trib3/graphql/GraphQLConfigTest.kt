package com.trib3.graphql

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.trib3.config.ConfigLoader
import org.testng.annotations.Test

class GraphQLConfigTest {
    companion object {
        val DEFAULT_KEEPALIVE = 15L
    }

    @Test
    fun testConfig() {
        val config = GraphQLConfig(ConfigLoader())
        assertThat(config.keepAliveIntervalSeconds).isEqualTo(DEFAULT_KEEPALIVE)
        assertThat(config.webSocketSubProtocol).isEqualTo("graphql-ws")
        for (i in listOf(
            config.asyncWriteTimeout,
            config.idleTimeout,
            config.maxBinaryMessageSize,
            config.maxTextMessageSize
        )) {
            assertThat(i).isNull()
        }
    }
}
