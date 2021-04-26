package com.trib3.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.testng.annotations.Test

class ExtensionsTest {
    @Test
    fun testApplyIf() {
        val s = "test string"
        assertThat(s.runIf(s.startsWith("test")) { uppercase() }).isEqualTo("TEST STRING")
        assertThat(s.runIf(s.startsWith("string")) { uppercase() }).isEqualTo("test string")
    }
}
