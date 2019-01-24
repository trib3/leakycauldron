package com.trib3.server.modules

import assertk.all
import assertk.assert
import assertk.assertions.contains
import io.dropwizard.jersey.jackson.JacksonBinder
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
        assert(resources.map { it -> it::class }).all {
            contains(ExceptionMapperBinder::class)
            contains(JacksonBinder::class)
        }
    }
}
