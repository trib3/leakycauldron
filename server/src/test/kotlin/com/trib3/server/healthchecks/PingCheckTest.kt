package com.trib3.server.healthchecks

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.testng.annotations.Test

class PingCheckTest {
    @Test
    fun testHealthCheck() {
        val check = PingHealthCheck()
        val result = check.check()
        assertThat(result.isHealthy).isTrue()
        assertThat(result.message).isEqualTo("pong")
    }
}
