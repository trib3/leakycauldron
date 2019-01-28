package com.trib3.server.modules

import assertk.assertThat
import assertk.assertions.contains
import org.testng.annotations.Guice
import org.testng.annotations.Test
import javax.inject.Inject
import javax.inject.Named

private class ArbitraryStringTribeAppModule : TribeApplicationModule() {
    override fun configure() {
        resourceBinder().addBinding().toInstance("arbitraryString".intern())
    }
}

@Guice(modules = [ArbitraryStringTribeAppModule::class])
class TribeApplicationModuleTest
@Inject constructor(
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    val bound: Set<@JvmSuppressWildcards Any>
) {
    @Test
    fun testResourceBound() {
        assertThat(bound).contains("arbitraryString".intern())
    }
}
