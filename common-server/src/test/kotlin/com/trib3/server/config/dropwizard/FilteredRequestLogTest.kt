package com.trib3.server.config.dropwizard

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import ch.qos.logback.access.spi.IAccessEvent
import ch.qos.logback.core.AppenderBase
import com.google.common.collect.ImmutableList
import io.dropwizard.logging.AppenderFactory
import org.easymock.EasyMock
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.testng.annotations.Test
import javax.servlet.http.HttpServletResponse

class FilteredRequestLogTest {
    @Test
    fun testLogFilterExcludesPing() {
        val events = mutableListOf<IAccessEvent>()
        val factory = FilteredLogbackAccessRequestLogFactory()
        factory.appenders =
            ImmutableList.of(
                AppenderFactory<IAccessEvent> { _,
                                                _,
                                                _,
                                                _,
                                                _
                    ->
                    object : AppenderBase<IAccessEvent>() {
                        override fun append(eventObject: IAccessEvent) {
                            events.add(eventObject)
                        }
                    }.also { it.start() }
                })
        val logger = factory.build("test")
        val mockRequest = EasyMock.niceMock<Request>(Request::class.java)
        val mockResponse = EasyMock.niceMock<Response>(Response::class.java)
        EasyMock.expect(mockRequest.requestURI).andReturn("/app/ping").anyTimes()
        EasyMock.expect(mockRequest.timeStamp).andReturn(System.currentTimeMillis() + 200).anyTimes()
        EasyMock.expect(mockResponse.status).andReturn(HttpServletResponse.SC_OK).anyTimes()
        EasyMock.replay(mockRequest, mockResponse)
        logger.log(mockRequest, mockResponse)
        assertThat(events).isEmpty()
    }

    @Test
    fun testLogFilterIncludesSlowPing() {
        val events = mutableListOf<IAccessEvent>()
        val factory = FilteredLogbackAccessRequestLogFactory()
        factory.appenders =
            ImmutableList.of(
                AppenderFactory<IAccessEvent> { _,
                                                _,
                                                _,
                                                _,
                                                _
                    ->
                    object : AppenderBase<IAccessEvent>() {
                        override fun append(eventObject: IAccessEvent) {
                            events.add(eventObject)
                        }
                    }.also { it.start() }
                })
        val logger = factory.build("test")
        val mockRequest = EasyMock.niceMock<Request>(Request::class.java)
        val mockResponse = EasyMock.niceMock<Response>(Response::class.java)
        EasyMock.expect(mockRequest.requestURI).andReturn("/app/ping").anyTimes()
        EasyMock.expect(mockRequest.timeStamp).andReturn(System.currentTimeMillis() - 300).anyTimes()
        EasyMock.expect(mockResponse.status).andReturn(HttpServletResponse.SC_OK).anyTimes()
        EasyMock.replay(mockRequest, mockResponse)
        logger.log(mockRequest, mockResponse)
        assertThat(events).isNotEmpty()
    }

    @Test
    fun testLogFilterIncludesFailedPing() {
        val events = mutableListOf<IAccessEvent>()
        val factory = FilteredLogbackAccessRequestLogFactory()
        factory.appenders =
            ImmutableList.of(
                AppenderFactory<IAccessEvent> { _,
                                                _,
                                                _,
                                                _,
                                                _
                    ->
                    object : AppenderBase<IAccessEvent>() {
                        override fun append(eventObject: IAccessEvent) {
                            events.add(eventObject)
                        }
                    }.also { it.start() }
                })
        val logger = factory.build("test")
        val mockRequest = EasyMock.niceMock<Request>(Request::class.java)
        val mockResponse = EasyMock.niceMock<Response>(Response::class.java)
        EasyMock.expect(mockRequest.requestURI).andReturn("/app/ping").anyTimes()
        EasyMock.expect(mockRequest.timeStamp).andReturn(System.currentTimeMillis() + 200).anyTimes()
        EasyMock.expect(mockResponse.status).andReturn(HttpServletResponse.SC_SERVICE_UNAVAILABLE).anyTimes()
        EasyMock.replay(mockRequest, mockResponse)
        logger.log(mockRequest, mockResponse)
        assertThat(events).isNotEmpty()
    }

    @Test
    fun testLogFilterIncludesArbitrary() {
        val events = mutableListOf<IAccessEvent>()
        val factory = FilteredLogbackAccessRequestLogFactory()
        factory.appenders =
            ImmutableList.of(
                AppenderFactory<IAccessEvent> { _,
                                                _,
                                                _,
                                                _,
                                                _
                    ->
                    object : AppenderBase<IAccessEvent>() {
                        override fun append(eventObject: IAccessEvent) {
                            events.add(eventObject)
                        }
                    }.also { it.start() }
                })
        val logger = factory.build("test")
        val mockRequest = EasyMock.niceMock<Request>(Request::class.java)
        val mockResponse = EasyMock.niceMock<Response>(Response::class.java)
        EasyMock.replay(mockRequest, mockResponse)
        logger.log(mockRequest, mockResponse)
        assertThat(events).isNotEmpty()
    }
}
