package com.trib3.server.modules

import assertk.all
import assertk.assert
import assertk.assertions.contains
import assertk.assertions.isEmpty
import com.codahale.metrics.health.HealthCheck
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.healthchecks.VersionHealthCheck
import io.dropwizard.Bundle
import org.testng.annotations.Guice
import org.testng.annotations.Test
import javax.inject.Inject

@Guice(modules = [DropwizardApplicationModule::class])
class DropwizardApplicationModuleTest
@Inject constructor(
    val healthchecks: Set<@JvmSuppressWildcards HealthCheck>,
    val bundles: Set<@JvmSuppressWildcards Bundle>
) {
    @Test
    fun testBindings() {
        assert(healthchecks.map { it -> it::class }).all {
            contains(VersionHealthCheck::class)
            contains(PingHealthCheck::class)
        }
        assert(bundles).isEmpty()
    }
}