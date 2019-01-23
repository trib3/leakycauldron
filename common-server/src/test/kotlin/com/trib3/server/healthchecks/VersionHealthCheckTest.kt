package com.trib3.server.healthchecks

import assertk.assert
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import org.testng.annotations.Test

class VersionHealthCheckTest {
    @Test
    fun testHealthCheck() {
        val check = VersionHealthCheck()
        val info = check.info()
        val result = check.check()
        assert(info).isEqualTo(result.message)
        assert(result.isHealthy).isTrue()
        assert(info).isNotEqualTo("null null")
        assert(info).doesNotContain("Unable to read version info")
    }
}