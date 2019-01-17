package com.trib3.modules

import com.codahale.metrics.health.HealthCheck
import com.google.inject.multibindings.Multibinder
import com.trib3.healthchecks.PingHealthCheck
import com.trib3.healthchecks.VersionHealthCheck
import io.dropwizard.Bundle

/**
 * The default module for running as a dropwizard application.  Binds common functionality for all services.
 */
class DropwizardApplicationModule : TribeApplicationModule() {
    override fun configure() {
        // Bind common health checks
        val healthChecks = Multibinder.newSetBinder(
            binder(),
            HealthCheck::class.java
        )
        healthChecks.addBinding().to(PingHealthCheck::class.java)
        healthChecks.addBinding().to(VersionHealthCheck::class.java)

        // Set up a Bundle multibinder, but no Bundles to bind by default right now
        Multibinder.newSetBinder(binder(), Bundle::class.java)
    }
}