package com.trib3.server.modules

import com.authzee.kotlinguice4.multibindings.KotlinMultibinder
import com.codahale.metrics.health.HealthCheck
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.healthchecks.VersionHealthCheck
import io.dropwizard.Bundle

/**
 * The default module for running as a dropwizard application.  Binds common functionality for all services.
 */
class DropwizardApplicationModule : TribeApplicationModule() {
    override fun configure() {
        // Bind common health checks
        val healthChecks = KotlinMultibinder.newSetBinder<HealthCheck>(kotlinBinder)
        healthChecks.addBinding().to(PingHealthCheck::class.java)
        healthChecks.addBinding().to(VersionHealthCheck::class.java)

        // Set up a Bundle multibinder, but no Bundles to bind by default right now
        KotlinMultibinder.newSetBinder<Bundle>(kotlinBinder)
    }
}