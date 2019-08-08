package com.trib3.server.resources

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.trib3.testing.server.ResourceTestBase
import org.testng.annotations.Test

class PingResourceTest : ResourceTestBase<PingResource>() {

    override fun getResource(): PingResource = PingResource()

    @Test
    fun testPing() {
        val ping = resource.target("/ping").request().get()
        assertThat(ping.status).isEqualTo(200)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("pong")
    }
}
