package com.trib3.server

import assertk.all
import assertk.assert
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.codahale.metrics.health.HealthCheckRegistry
import com.fasterxml.jackson.databind.DeserializationFeature
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.healthchecks.VersionHealthCheck
import com.trib3.server.logging.RequestIdFilter
import io.dropwizard.Configuration
import io.dropwizard.jersey.DropwizardResourceConfig
import io.dropwizard.jersey.setup.JerseyEnvironment
import io.dropwizard.jetty.setup.ServletEnvironment
import io.dropwizard.setup.AdminEnvironment
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import io.swagger.v3.jaxrs2.integration.OpenApiServlet
import org.easymock.EasyMock
import org.easymock.EasyMock.anyObject
import org.easymock.EasyMock.anyString
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
        assert(instance.name).all {
            isEqualTo("Test")
            isEqualTo(instance.appConfig.appName)
        }
        assert(instance.healthChecks.map { it::class }).all {
            contains(VersionHealthCheck::class)
            contains(PingHealthCheck::class)
        }
        assert(instance.servletFilterConfigs.map { it.filterClass }).all {
            contains(RequestIdFilter::class.java)
            contains(CrossOriginFilter::class.java)
        }
        assert(instance.adminServlets.map { it.name }).all {
            contains("SwaggerAssetServlet")
            contains(OpenApiServlet::class.simpleName)
        }
        assert(instance.versionHealthCheck).isNotNull()
        assert(instance.appServlets).isNotNull()
        assert(instance.dropwizardBundles).isNotNull()
        assert(instance.jerseyResources).isNotNull()
        assert(instance.jaxrsAppProcessors).isNotNull()
    }

    @Test
    fun testBootstrap() {
        val bootstrap = Bootstrap<Configuration>(instance)
        instance.initialize(bootstrap)
        assert(bootstrap.objectMapper).isEqualTo(instance.objectMapper)
        assert(bootstrap.objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse()
        assert(bootstrap.configurationFactoryFactory).all {
            isEqualTo(instance.configurationFactoryFactory)
            isInstanceOf(HoconConfigurationFactoryFactory::class)
        }
    }

    @Test
    fun testRun() {
        val mockConf = EasyMock.mock(Configuration::class.java)
        val mockEnv = EasyMock.mock(Environment::class.java)
        val mockJersey = EasyMock.mock(JerseyEnvironment::class.java)
        val mockAdmin = EasyMock.mock(AdminEnvironment::class.java)
        val mockServlet = EasyMock.mock(ServletEnvironment::class.java)
        val mockHealthChecks = EasyMock.mock(HealthCheckRegistry::class.java)
        val mockServletRegistration = EasyMock.niceMock(ServletRegistration.Dynamic::class.java)
        val mockFilterRegistration = EasyMock.niceMock(FilterRegistration.Dynamic::class.java)
        EasyMock.expect(mockEnv.jersey()).andReturn(mockJersey).anyTimes()
        EasyMock.expect(mockJersey.resourceConfig).andReturn(DropwizardResourceConfig()).anyTimes()
        EasyMock.expect(mockEnv.admin()).andReturn(mockAdmin).anyTimes()
        EasyMock.expect(mockAdmin.addServlet(anyString(), anyObject<Servlet>()))
            .andReturn(mockServletRegistration)
            .anyTimes()
        EasyMock.expect(mockEnv.servlets()).andReturn(mockServlet).anyTimes()
        EasyMock.expect(mockEnv.healthChecks()).andReturn(mockHealthChecks).anyTimes()
        EasyMock.expect(mockServlet.addFilter(anyString(), EasyMock.anyObject<Class<out Filter>>()))
            .andReturn(mockFilterRegistration).anyTimes()
        EasyMock.expect(mockHealthChecks.register(anyString(), anyObject<VersionHealthCheck>())).once()
        EasyMock.expect(mockHealthChecks.register(anyString(), anyObject<PingHealthCheck>())).once()

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
