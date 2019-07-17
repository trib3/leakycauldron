package com.trib3.server.modules

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.size
import com.codahale.metrics.health.HealthCheck
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.filters.RequestIdFilter
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.healthchecks.VersionHealthCheck
import com.trib3.server.resources.GraphQLResource
import io.dropwizard.Configuration
import io.dropwizard.configuration.ConfigurationFactoryFactory
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.testng.annotations.Guice
import org.testng.annotations.Test
import javax.inject.Inject
import javax.inject.Named

class DummyQuery {
    fun query(): String {
        return "test"
    }
}

@Guice(modules = [DefaultApplicationModule::class])
class DefaultApplicationModuleTest
@Inject constructor(
    val configurationFactoryFactory: ConfigurationFactoryFactory<Configuration>,
    val servletFilterConfigs: Set<@JvmSuppressWildcards ServletFilterConfig>,
    val healthChecks: Set<@JvmSuppressWildcards HealthCheck>,
    val objectMapper: ObjectMapper,
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    val resources: Set<@JvmSuppressWildcards Any>
) {
    @Test
    fun testBindings() {
        assertThat(resources.filterIsInstance<GraphQLResource>()).size().isGreaterThan(0)
        assertThat(healthChecks.map { it::class }).all {
            contains(VersionHealthCheck::class)
            contains(PingHealthCheck::class)
        }
        assertThat(configurationFactoryFactory).isInstanceOf(HoconConfigurationFactoryFactory::class)
        assertThat(servletFilterConfigs.map { it.filterClass }).all {
            contains(RequestIdFilter::class.java)
            contains(CrossOriginFilter::class.java)
        }
        assertThat(servletFilterConfigs.map { it.name }).all {
            contains(RequestIdFilter::class.simpleName)
            contains(CrossOriginFilter::class.simpleName)
        }
        assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse()
    }

    @Test
    fun testGraphQLProvider() {

        val module = DefaultApplicationModule()
        val graphQLInstance = module.provideGraphQLInstance(
            setOf(),
            setOf(DummyQuery()),
            setOf(),
            setOf()
        )
        assertThat(graphQLInstance).isNotNull()
    }
}
