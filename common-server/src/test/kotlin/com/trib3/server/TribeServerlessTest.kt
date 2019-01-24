package com.trib3.server

import assertk.all
import assertk.assert
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.amazonaws.serverless.proxy.model.AwsProxyRequest
import com.amazonaws.services.lambda.runtime.Context
import com.fasterxml.jackson.databind.DeserializationFeature
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.logging.RequestIdFilter
import io.dropwizard.Configuration
import io.dropwizard.setup.Bootstrap
import org.easymock.EasyMock
import org.testng.annotations.Test

class TribeServerlessTest {
    val instance = TribeServerlessApp.INSTANCE

    @Test
    fun testFields() {
        assert(instance.name).all {
            isEqualTo("Test")
            isEqualTo(instance.appConfig.serviceName)
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
        val mockRequest = EasyMock.niceMock(AwsProxyRequest::class.java)
        EasyMock.expect(mockRequest.path).andReturn("/").anyTimes()
        EasyMock.expect(mockRequest.httpMethod).andReturn("GET").anyTimes()
        val mockContext = EasyMock.niceMock(Context::class.java)
        EasyMock.replay(mockRequest, mockContext)
        val proxy = instance.proxy
        val response = proxy.proxy(mockRequest, mockContext)
        assert(response.statusCode).isEqualTo(404)
        instance.servletFilters.forEach {
            assert(proxy.servletContext.getFilterRegistration(it.simpleName)).isNotNull()
        }
    }

    @Test
    fun testLambdaEntry() {
        val lambda = TribeServerless()
        val mockRequest = EasyMock.niceMock(AwsProxyRequest::class.java)
        EasyMock.expect(mockRequest.path).andReturn("/").anyTimes()
        EasyMock.expect(mockRequest.httpMethod).andReturn("GET").anyTimes()
        val mockContext = EasyMock.niceMock(Context::class.java)
        EasyMock.replay(mockRequest, mockContext)
        val response = lambda.handleRequest(mockRequest, mockContext)
        assert(response.statusCode).isEqualTo(404)
    }
}
