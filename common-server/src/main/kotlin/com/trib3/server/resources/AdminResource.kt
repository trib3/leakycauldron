package com.trib3.server.resources

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.codahale.metrics.jvm.ThreadDump
import com.google.inject.Inject
import com.trib3.server.config.TribeApplicationConfig
import java.lang.management.ManagementFactory
import java.util.SortedMap
import javax.ws.rs.GET
import javax.ws.rs.NotAuthorizedException
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.StreamingOutput

/**
 * A Resource that exposes similar information to dropwizard's AdminServlet from
 * a JAX-RS context instead of a servlet context
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
class AdminResource
@Inject constructor(
    internal val appConfig: TribeApplicationConfig,
    internal val healthCheckRegistry: HealthCheckRegistry,
    internal val metricRegistry: MetricRegistry
) {
    private val threadDumper = try {
        ThreadDump(ManagementFactory.getThreadMXBean())
    } catch (e: NoClassDefFoundError) {
        null
    }

    /**
     * Verifies an auth token is correct if configured
     */
    private fun checkAccess(key: String?) {
        appConfig.adminAuthToken?.let {
            if (it != key) {
                throw NotAuthorizedException(Response.status(Response.Status.UNAUTHORIZED).build())
            }
        }
    }

    /**
     * Returns system metrics
     */
    @GET
    @Path("/metrics")
    fun getMetrics(@QueryParam("key") key: String?): MetricRegistry {
        checkAccess(key)
        return metricRegistry
    }

    /**
     * Returns system health checks
     */
    @GET
    @Path("/healthcheck")
    fun getHeathCheck(@QueryParam("key") key: String?): SortedMap<String, HealthCheck.Result> {
        checkAccess(key)
        return healthCheckRegistry.runHealthChecks()
    }

    /**
     * Return system thread dump
     */
    @GET
    @Path("/threads")
    @Produces(MediaType.TEXT_PLAIN)
    fun getThreads(@QueryParam("key") key: String?): Response {
        checkAccess(key)
        val responseBuilder = Response.ok()
        threadDumper?.let {
            val output = StreamingOutput { output -> it.dump(output) }
            responseBuilder.entity(output)
        }
        return responseBuilder.build()
    }
}
