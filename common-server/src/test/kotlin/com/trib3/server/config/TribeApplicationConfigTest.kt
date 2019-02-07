package com.trib3.server.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.trib3.config.ConfigLoader
import com.trib3.config.KMSStringSelectReader
import org.testng.annotations.Test

class TribeApplicationConfigTest {
    @Test
    fun testConfig() {
        val config = TribeApplicationConfig(ConfigLoader(KMSStringSelectReader(null)))
        assertThat(config.env).isEqualTo("dev")
        assertThat(config.appName).isEqualTo("Test")
        assertThat(config.adminAuthToken).isEqualTo("SECRET")
    }
}
