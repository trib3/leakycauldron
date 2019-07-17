package com.trib3.server.modules

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import io.dropwizard.jersey.jackson.JacksonFeature
import io.dropwizard.setup.ExceptionMapperBinder
import org.testng.annotations.Guice
import org.testng.annotations.Test
import javax.inject.Inject
import javax.inject.Named

@Guice(modules = [ServerlessApplicationModule::class])
class ServerlessApplicationModuleTest
@Inject constructor(
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    val resources: Set<@JvmSuppressWildcards Any>
) {
    @Test
    fun testBindings() {
        assertThat(resources.map { it::class }).all {
            contains(ExceptionMapperBinder::class)
            contains(JacksonFeature::class)
        }
    }

    @Test
    fun testModuleEquals() {
        assertThat(ServerlessApplicationModule()).isEqualTo(ServerlessApplicationModule())
    }
}
