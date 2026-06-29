package com.trib3.server.modules

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.codahale.metrics.health.HealthCheck
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.filters.RequestIdFilter
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.healthchecks.VersionHealthCheck
import io.dropwizard.auth.AuthFilter
import io.dropwizard.auth.Authorizer
import io.dropwizard.configuration.ConfigurationFactoryFactory
import io.dropwizard.core.Configuration
import jakarta.annotation.Nullable
import jakarta.inject.Inject
import jakarta.inject.Named
import org.eclipse.jetty.ee10.servlets.HeaderFilter
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.handler.CrossOriginHandler
import org.testng.annotations.Guice
import org.testng.annotations.Test
import java.security.Principal

@Guice(modules = [DefaultApplicationModule::class])
class DefaultApplicationModuleTest
    @Inject
    constructor(
        val configurationFactoryFactory: ConfigurationFactoryFactory<Configuration>,
        val servletFilterConfigs: Set<ServletFilterConfig>,
        val healthChecks: Set<HealthCheck>,
        val objectMapper: ObjectMapper,
        @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
        val resources: Set<Any>,
        val crossOriginHandler: Handler.Singleton,
        @Nullable val authFilter: AuthFilter<*, *>?,
        @Nullable val authorizer: Authorizer<Principal>?,
    ) {
        @Test
        fun testBindings() {
            assertThat(healthChecks.map { it::class }).all {
                contains(VersionHealthCheck::class)
                contains(PingHealthCheck::class)
            }
            assertThat(configurationFactoryFactory).isInstanceOf(HoconConfigurationFactoryFactory::class)
            assertThat(servletFilterConfigs.map { it.filterClass }).contains(RequestIdFilter::class.java)
            assertThat(servletFilterConfigs.map { it.name }).all {
                contains(RequestIdFilter::class.simpleName)
                contains(HeaderFilter::class.simpleName)
            }
            assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse()
            assertThat(crossOriginHandler).isInstanceOf<CrossOriginHandler>()
            assertThat(DefaultApplicationModule()).isEqualTo(DefaultApplicationModule())
            assertThat(authFilter).isNull()
            assertThat(authorizer).isNull()
        }
    }
