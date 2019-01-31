package com.trib3.server.modules

import com.authzee.kotlinguice4.multibindings.KotlinMultibinder
import com.codahale.metrics.health.HealthCheck
import com.trib3.config.modules.KMSModule
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.healthchecks.VersionHealthCheck
import com.trib3.server.swagger.JaxrsAppProcessor
import com.trib3.server.swagger.SwaggerInitializer
import io.dropwizard.Bundle
import io.dropwizard.servlets.assets.AssetServlet
import io.swagger.v3.jaxrs2.integration.OpenApiServlet

/**
 * The default module for running as a dropwizard application.  Binds common functionality for all services.
 */
class DropwizardApplicationModule : TribeApplicationModule() {
    override fun configure() {
        install(KMSModule())
        // Bind common health checks
        val healthChecks = KotlinMultibinder.newSetBinder<HealthCheck>(kotlinBinder)
        healthChecks.addBinding().to(PingHealthCheck::class.java)
        healthChecks.addBinding().to(VersionHealthCheck::class.java)
        // Bind admin servlets for swagger usage
        adminServletBinder().addBinding().toInstance(
            ServletConfig(
                OpenApiServlet::class.java.simpleName,
                OpenApiServlet(),
                listOf("/openapi")
            )
        )
        adminServletBinder().addBinding().toInstance(
            ServletConfig(
                "SwaggerAssetServlet",
                AssetServlet(
                    "swagger",
                    "/swagger",
                    "index.html",
                    Charsets.UTF_8
                ),
                listOf("/swagger", "/swagger/*")
            )
        )

        KotlinMultibinder.newSetBinder<JaxrsAppProcessor>(kotlinBinder).addBinding().to<SwaggerInitializer>()

        // Set up a Bundle multibinder, but no Bundles to bind by default right now
        KotlinMultibinder.newSetBinder<Bundle>(kotlinBinder)

        // Make sure the app servlet binder is set up
        appServletBinder()
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean {
        return other is DropwizardApplicationModule
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}
