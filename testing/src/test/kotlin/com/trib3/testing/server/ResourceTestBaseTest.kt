package com.trib3.testing.server

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.testng.annotations.Test
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.core.Context

@Path("/")
class SimpleResource {
    @GET
    fun getThing(@Context request: HttpServletRequest): String {
        return request.getHeader("Test-Header")
    }
}

class ResourceTestBaseJettyWebContainerTest : ResourceTestBase<SimpleResource>() {
    override fun getResource(): SimpleResource {
        return SimpleResource()
    }

    override fun getContainerFactory(): TestContainerFactory {
        return JettyWebTestContainerFactory()
    }

    @Test
    fun testSimpleResource() {
        val response = resource.target("/").request().header("Test-Header", "Test-Value").get()
        assertThat(response.status).isEqualTo(200)
        assertThat(response.readEntity(String::class.java)).isEqualTo("Test-Value")
    }
}

class ResourceTestBaseInMemoryContainerTest : ResourceTestBase<SimpleResource>() {
    override fun getResource(): SimpleResource {
        return SimpleResource()
    }

    @Test
    fun testSimpleResource() {
        val response = resource.target("/").request().header("Test-Header", "Test-Value").get()
        assertThat(response.status).isEqualTo(500)
    }
}
