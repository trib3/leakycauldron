package com.trib3.server.modules

import assertk.assertThat
import assertk.assertions.isEmpty
import io.dropwizard.Bundle
import org.testng.annotations.Guice
import org.testng.annotations.Test
import javax.inject.Inject

@Guice(modules = [DropwizardApplicationModule::class])
class DropwizardApplicationModuleTest
@Inject constructor(
    val bundles: Set<@JvmSuppressWildcards Bundle>
) {
    @Test
    fun testBindings() {
        assertThat(bundles).isEmpty()
    }
}
