package com.trib3.server.modules

import assertk.assertThat
import assertk.assertions.contains
import jakarta.inject.Inject
import jakarta.inject.Named
import org.testng.annotations.Guice
import org.testng.annotations.Test

private class ArbitraryStringTribeAppModule : TribeApplicationModule() {
    override fun configure() {
        resourceBinder().addBinding().toInstance("arbitraryString".intern())
    }
}

@Guice(modules = [ArbitraryStringTribeAppModule::class])
class TribeApplicationModuleTest
@Inject constructor(
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    val bound: Set<Any>,
) {
    @Test
    fun testResourceBound() {
        assertThat(bound).contains("arbitraryString".intern())
    }
}
