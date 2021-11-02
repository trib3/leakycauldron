package com.trib3.graphql

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.trib3.config.ConfigLoader
import org.testng.annotations.Test

class GraphQLConfigTest {
    companion object {
        const val DEFAULT_KEEPALIVE = 15L
    }

    @Test
    fun testConfig() {
        val config = GraphQLConfig(ConfigLoader())
        assertThat(config.keepAliveIntervalSeconds).isEqualTo(DEFAULT_KEEPALIVE)
        for (i in listOf(config.idleTimeout, config.maxBinaryMessageSize, config.maxTextMessageSize)) {
            assertThat(i).isNull()
        }
    }
}
