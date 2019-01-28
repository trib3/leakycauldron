package com.trib3.server.healthchecks

import assertk.assertThat
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
        assertThat(info).isEqualTo(result.message)
        assertThat(result.isHealthy).isTrue()
        assertThat(info).isNotEqualTo("null null")
        assertThat(info).doesNotContain("Unable to read version info")
    }
}
