package com.trib3.server.modules

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.dropwizard.Configuration
import io.dropwizard.ConfiguredBundle
import org.testng.annotations.Guice
import org.testng.annotations.Test
import javax.inject.Inject
import javax.inject.Named

@Guice(modules = [DropwizardApplicationModule::class])
class DropwizardApplicationModuleTest
@Inject constructor(
    val bundles: Set<@JvmSuppressWildcards ConfiguredBundle<Configuration>>,
    @Named(TribeApplicationModule.ADMIN_SERVLET_FILTERS_BIND_NAME)
    val adminFilters: Set<@JvmSuppressWildcards ServletFilterConfig>
) {
    @Test
    fun testBindings() {
        assertThat(bundles).isEmpty()
        assertThat(adminFilters).isNotNull()
    }

    @Test
    fun testModuleEquals() {
        assertThat(DropwizardApplicationModule()).isEqualTo(DropwizardApplicationModule())
    }
}
