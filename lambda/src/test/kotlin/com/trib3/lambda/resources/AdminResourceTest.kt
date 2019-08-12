package com.trib3.lambda.resources

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.codahale.metrics.jvm.JvmAttributeGaugeSet
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.modules.DefaultApplicationModule
import com.trib3.testing.server.ResourceTestBase
import org.testng.annotations.Guice
import org.testng.annotations.Test
import java.lang.management.ManagementFactory
import javax.inject.Inject
import javax.ws.rs.core.GenericType
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Guice(modules = [DefaultApplicationModule::class])
class AdminResourceTest
@Inject constructor(val adminResource: AdminResource) : ResourceTestBase<AdminResource>() {
    val adminAuthToken = adminResource.appConfig.adminAuthToken

    init {
        adminResource.metricRegistry.register("jvm.attribute", JvmAttributeGaugeSet())
        adminResource.healthCheckRegistry.register("ping", PingHealthCheck())
    }

    override fun getResource(): AdminResource {
        return adminResource
    }

    @Test
    fun testUnauthorized() {
        listOf("/admin/threads", "/admin/metrics", "/admin/healthcheck").forEach {
            val response = resource.target(it).request().get()
            assertThat(response.status).isEqualTo(Response.Status.UNAUTHORIZED.statusCode)
        }
    }

    @Test
    fun testThread() {
        val dump = resource.target("/admin/threads")
            .queryParam("key", adminAuthToken)
            .request().get()
            .readEntity(String::class.java)
        assertThat(dump).contains("AdminResourceTest.testThread")
    }

    @Test
    fun testMetrics() {
        val metrics = resource.target("/admin/metrics")
            .queryParam("key", adminAuthToken)
            .request(MediaType.APPLICATION_JSON).get()
            .readEntity(object : GenericType<Map<String, Any>>() {})
        @Suppress("UNCHECKED_CAST") // in practice always strings here
        val gauges = metrics.getValue("gauges") as Map<String, Map<String, String>>
        assertThat(gauges.getValue("jvm.attribute.vendor").getValue("value"))
            .contains(ManagementFactory.getRuntimeMXBean().vmVendor)
    }

    @Test
    fun testHealth() {
        val health = resource.target("/admin/healthcheck")
            .queryParam("key", adminAuthToken)
            .request(MediaType.APPLICATION_JSON).get()
            .readEntity(object : GenericType<Map<String, Map<String, String>>>() {})
        assertThat(health.getValue("ping").getValue("healthy")).isEqualTo("true")
        assertThat(health.getValue("ping").getValue("message")).isEqualTo("pong")
    }
}
