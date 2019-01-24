package com.trib3.server.healthchecks

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.testng.annotations.Test

class PingCheckTest {
    @Test
    fun testHealthCheck() {
        val check = PingHealthCheck()
        val result = check.check()
        assert(result.isHealthy).isTrue()
        assert(result.message).isEqualTo("pong")
    }
}
