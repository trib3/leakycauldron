package com.trib3.server

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.codahale.metrics.health.HealthCheckRegistry
import com.fasterxml.jackson.databind.DeserializationFeature
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.filters.AdminAuthFilter
import com.trib3.server.filters.RequestIdFilter
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.healthchecks.VersionHealthCheck
import com.trib3.testing.LeakyMock
import io.dropwizard.Configuration
import io.dropwizard.jersey.DropwizardResourceConfig
import io.dropwizard.jersey.setup.JerseyEnvironment
import io.dropwizard.jetty.setup.ServletEnvironment
import io.dropwizard.setup.AdminEnvironment
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import io.swagger.v3.jaxrs2.integration.OpenApiServlet
import org.easymock.EasyMock
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.testng.annotations.Test
import javax.servlet.Filter
import javax.servlet.FilterRegistration
import javax.servlet.Servlet
import javax.servlet.ServletRegistration

class TribeApplicationTest {
    val instance = TribeApplication.INSTANCE

    @Test
    fun testFields() {
        assertThat(instance.name).all {
            isEqualTo("Test")
            isEqualTo(instance.appConfig.appName)
        }
        assertThat(instance.healthChecks.map { it::class }).all {
            contains(VersionHealthCheck::class)
            contains(PingHealthCheck::class)
        }
        assertThat(instance.servletFilterConfigs.map { it.filterClass }).all {
            contains(RequestIdFilter::class.java)
            contains(CrossOriginFilter::class.java)
        }
        assertThat(instance.adminServletFilterConfigs.map { it.filterClass }).contains(AdminAuthFilter::class.java)
        assertThat(instance.adminServlets.map { it.name }).all {
            contains("SwaggerAssetServlet")
            contains(OpenApiServlet::class.simpleName)
        }
        assertThat(instance.versionHealthCheck).isNotNull()
        assertThat(instance.appServlets).isNotNull()
        assertThat(instance.dropwizardBundles).isNotNull()
        assertThat(instance.jerseyResources).isNotNull()
        assertThat(instance.jaxrsAppProcessors).isNotNull()
    }

    @Test
    fun testBootstrap() {
        val bootstrap = Bootstrap<Configuration>(instance)
        instance.initialize(bootstrap)
        assertThat(bootstrap.metricRegistry).isEqualTo(instance.metricRegistry)
        assertThat(bootstrap.healthCheckRegistry).isEqualTo(instance.healthCheckRegistry)
        assertThat(bootstrap.objectMapper).isEqualTo(instance.objectMapper)
        assertThat(bootstrap.objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse()
        assertThat(bootstrap.configurationFactoryFactory).all {
            isEqualTo(instance.configurationFactoryFactory)
            isInstanceOf(HoconConfigurationFactoryFactory::class)
        }
    }

    @Test
    fun testRun() {
        val mockConf = LeakyMock.mock<Configuration>()
        val mockEnv = LeakyMock.mock<Environment>()
        val mockJersey = LeakyMock.niceMock<JerseyEnvironment>()
        val mockAdmin = LeakyMock.mock<AdminEnvironment>()
        val mockServlet = LeakyMock.mock<ServletEnvironment>()
        val mockHealthChecks = LeakyMock.mock<HealthCheckRegistry>()
        val mockServletRegistration =
            LeakyMock.niceMock<ServletRegistration.Dynamic>()
        val mockFilterRegistration =
            LeakyMock.niceMock<FilterRegistration.Dynamic>()
        EasyMock.expect(mockEnv.jersey()).andReturn(mockJersey).anyTimes()
        EasyMock.expect(mockJersey.resourceConfig).andReturn(DropwizardResourceConfig()).anyTimes()
        EasyMock.expect(mockEnv.admin()).andReturn(mockAdmin).anyTimes()
        EasyMock.expect(mockAdmin.addServlet(LeakyMock.anyString(), LeakyMock.anyObject<Servlet>()))
            .andReturn(mockServletRegistration)
            .anyTimes()
        EasyMock.expect(mockEnv.servlets()).andReturn(mockServlet).anyTimes()
        EasyMock.expect(mockEnv.healthChecks()).andReturn(mockHealthChecks).anyTimes()
        EasyMock.expect(mockServlet.addFilter(LeakyMock.anyString(), EasyMock.anyObject<Class<out Filter>>()))
            .andReturn(mockFilterRegistration).anyTimes()
        EasyMock.expect(mockServlet.addServlet(LeakyMock.anyString(), LeakyMock.anyObject<Servlet>()))
            .andReturn(mockServletRegistration).anyTimes()
        EasyMock.expect(mockAdmin.addFilter(LeakyMock.anyString(), EasyMock.anyObject<Class<out Filter>>()))
            .andReturn(mockFilterRegistration).anyTimes()
        EasyMock.expect(mockHealthChecks.register(LeakyMock.anyString(), LeakyMock.anyObject<VersionHealthCheck>()))
            .once()
        EasyMock.expect(mockHealthChecks.register(LeakyMock.anyString(), LeakyMock.anyObject<PingHealthCheck>())).once()

        EasyMock.replay(
            mockConf,
            mockEnv,
            mockAdmin,
            mockJersey,
            mockServlet,
            mockHealthChecks,
            mockServletRegistration,
            mockFilterRegistration
        )
        instance.run(mockConf, mockEnv)
        EasyMock.verify(
            mockConf,
            mockEnv,
            mockAdmin,
            mockJersey,
            mockServlet,
            mockHealthChecks,
            mockServletRegistration,
            mockFilterRegistration
        )
    }
}
