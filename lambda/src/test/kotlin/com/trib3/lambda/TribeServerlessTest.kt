@file:Suppress("Deprecation")

package com.trib3.lambda

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.amazonaws.serverless.proxy.model.AwsProxyRequest
import com.amazonaws.serverless.proxy.model.AwsProxyRequestContext
import com.amazonaws.serverless.proxy.model.Headers
import com.amazonaws.services.lambda.runtime.Context
import com.fasterxml.jackson.databind.DeserializationFeature
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.filters.RequestIdFilter
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.healthchecks.VersionHealthCheck
import com.trib3.testing.LeakyMock
import org.easymock.EasyMock
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.testng.annotations.Test

class TribeServerlessTest {
    val instance = TribeServerlessApp.INSTANCE

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
        assertThat(instance.versionHealthCheck).isNotNull()
        assertThat(instance.jerseyResources).isNotNull()
    }

    @Test
    fun testBootstrap() {
        val bootstrap = instance.bootstrap
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
    fun testRunAndEntry() {
        val mockProxyRequest = LeakyMock.niceMock<AwsProxyRequestContext>()
        val mockRequest = LeakyMock.niceMock<AwsProxyRequest>()
        val mockHeaders = LeakyMock.niceMock<Headers>()
        EasyMock.expect(mockRequest.path).andReturn("/").anyTimes()
        EasyMock.expect(mockRequest.httpMethod).andReturn("GET").anyTimes()
        EasyMock.expect(mockRequest.requestContext).andReturn(mockProxyRequest).anyTimes()
        EasyMock.expect(mockRequest.multiValueHeaders).andReturn(mockHeaders).anyTimes()
        EasyMock.expect(mockHeaders.keys).andReturn(mutableSetOf()).anyTimes()
        val mockContext = LeakyMock.niceMock<Context>()
        EasyMock.replay(mockRequest, mockContext, mockProxyRequest, mockHeaders)
        val proxy = instance.proxy
        proxy.setLogFormatter(null)
        val response = proxy.proxy(mockRequest, mockContext)
        assertThat(response.statusCode).isEqualTo(404)
        instance.servletFilterConfigs.forEach {
            assertThat(proxy.servletContext.getFilterRegistration(it.filterClass.simpleName)).isNotNull()
        }
        val lambda = TribeServerless()
        val lambdaResponse = lambda.handleRequest(mockRequest, mockContext)
        assertThat(lambdaResponse.statusCode).isEqualTo(404)
    }
}
