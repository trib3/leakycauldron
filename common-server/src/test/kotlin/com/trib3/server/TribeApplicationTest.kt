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
import io.dropwizard.jersey.setup.JerseyEnvironment
import io.dropwizard.jetty.setup.ServletEnvironment
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import org.easymock.EasyMock
import org.easymock.EasyMock.anyObject
import org.easymock.EasyMock.anyString
import org.easymock.EasyMock.eq
import org.testng.annotations.Test
import javax.servlet.FilterRegistration

class TribeApplicationTest {
    val instance = TribeApplication.INSTANCE

    @Test
    fun testFields() {
        assert(instance.name).all {
            isEqualTo("Test")
            isEqualTo(instance.appConfig.serviceName)
        }
        assert(instance.healthChecks.map { it -> it::class }).all {
            contains(VersionHealthCheck::class)
            contains(PingHealthCheck::class)
        }
        assert(instance.servletFilters).all {
            contains(RequestIdFilter::class.java)
        }
        assert(instance.versionHealthCheck).isNotNull()
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
        val mockServlet = EasyMock.mock(ServletEnvironment::class.java)
        val mockHealthChecks = EasyMock.mock(HealthCheckRegistry::class.java)
        EasyMock.expect(mockEnv.jersey()).andReturn(mockJersey).anyTimes()
        EasyMock.expect(mockEnv.servlets()).andReturn(mockServlet).anyTimes()
        EasyMock.expect(mockEnv.healthChecks()).andReturn(mockHealthChecks).anyTimes()
        EasyMock.expect(mockServlet.addFilter(anyString(), eq(RequestIdFilter::class.java))).andReturn(
            EasyMock.mock(
                FilterRegistration.Dynamic::class.java
            )
        ).once()
        EasyMock.expect(mockHealthChecks.register(anyString(), anyObject(VersionHealthCheck::class.java))).once()
        EasyMock.expect(mockHealthChecks.register(anyString(), anyObject(PingHealthCheck::class.java))).once()

        EasyMock.replay(mockConf, mockEnv, mockJersey, mockServlet, mockHealthChecks)
        instance.run(mockConf, mockEnv)
        EasyMock.verify(mockConf, mockEnv, mockJersey, mockServlet, mockHealthChecks)
    }
}