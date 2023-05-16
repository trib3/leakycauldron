package com.trib3.server.config.dropwizard

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.each
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import ch.qos.logback.access.spi.IAccessEvent
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.AppenderBase
import com.google.common.collect.ImmutableList
import com.trib3.testing.LeakyMock
import io.dropwizard.logging.common.AppenderFactory
import jakarta.servlet.http.HttpServletResponse
import org.easymock.EasyMock
import org.eclipse.jetty.http.HttpField
import org.eclipse.jetty.http.HttpFields
import org.eclipse.jetty.http.MetaData
import org.eclipse.jetty.server.HttpChannel
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.slf4j.LoggerFactory
import org.testng.annotations.Test
import java.util.TimeZone

class FilteredRequestLogTest {
    @Test
    fun testLogFilterExcludesPing() {
        val events = mutableListOf<IAccessEvent>()
        val factory = FilteredLogbackAccessRequestLogFactory()
        factory.appenders =
            ImmutableList.of(
                AppenderFactory<IAccessEvent> {
                        _,
                        _,
                        _,
                        _,
                        _,
                    ->
                    object : AppenderBase<IAccessEvent>() {
                        override fun append(eventObject: IAccessEvent) {
                            events.add(eventObject)
                        }
                    }.also { it.start() }
                },
            )
        val logger = factory.build("test")
        val mockRequest = LeakyMock.niceMock<Request>()
        val mockResponse = LeakyMock.niceMock<Response>()
        EasyMock.expect(mockRequest.requestURI).andReturn("/app/ping").anyTimes()
        EasyMock.expect(mockRequest.timeStamp).andReturn(System.currentTimeMillis() + 200).anyTimes()
        EasyMock.expect(mockResponse.committedMetaData).andReturn(
            MetaData.Response(null, HttpServletResponse.SC_OK, null),
        ).anyTimes()
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
                AppenderFactory<IAccessEvent> {
                        _,
                        _,
                        _,
                        _,
                        _,
                    ->
                    object : AppenderBase<IAccessEvent>() {
                        override fun append(eventObject: IAccessEvent) {
                            events.add(eventObject)
                        }
                    }.also { it.start() }
                },
            )
        val logger = factory.build("test")
        val mockRequest = LeakyMock.niceMock<Request>()
        val mockResponse = LeakyMock.niceMock<Response>()
        EasyMock.expect(mockRequest.requestURI).andReturn("/app/ping").anyTimes()
        EasyMock.expect(mockRequest.timeStamp).andReturn(System.currentTimeMillis() - 300).anyTimes()
        EasyMock.expect(mockResponse.committedMetaData).andReturn(
            MetaData.Response(null, HttpServletResponse.SC_OK, null),
        ).anyTimes()
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
                AppenderFactory<IAccessEvent> {
                        _,
                        _,
                        _,
                        _,
                        _,
                    ->
                    object : AppenderBase<IAccessEvent>() {
                        override fun append(eventObject: IAccessEvent) {
                            events.add(eventObject)
                        }
                    }.also { it.start() }
                },
            )
        val logger = factory.build("test")
        val mockRequest = LeakyMock.niceMock<Request>()
        val mockResponse = LeakyMock.niceMock<Response>()
        val fakeRequestId = "f74663ea-4da5-4890-91a7-e63f8d1ba82c"
        val fields = HttpFields.build(HttpFields.from(HttpField(null, "X-Request-Id", fakeRequestId)))
        EasyMock.expect(mockRequest.requestURI).andReturn("/app/ping").anyTimes()
        EasyMock.expect(mockRequest.timeStamp).andReturn(System.currentTimeMillis() + 200).anyTimes()
        EasyMock.expect(mockResponse.committedMetaData).andReturn(
            MetaData.Response(null, HttpServletResponse.SC_SERVICE_UNAVAILABLE, fields),
        ).anyTimes()
        val mockChannel = LeakyMock.niceMock<HttpChannel>()
        EasyMock.expect(mockResponse.httpChannel).andReturn(mockChannel).anyTimes()
        EasyMock.expect(mockResponse.getHeader("X-Request-Id")).andReturn(fakeRequestId).anyTimes()
        EasyMock.expect(mockResponse.httpFields).andReturn(fields).anyTimes()
        EasyMock.replay(mockRequest, mockResponse)

        logger.log(mockRequest, mockResponse)
        assertThat(events).isNotEmpty()

        val context = (LoggerFactory.getLogger("http.request") as Logger).loggerContext
        val layoutFactory = RequestIdLogbackAccessRequestLayoutFactory()
        val layout = layoutFactory.build(context, TimeZone.getDefault()).also { it.start() }
        assertThat(events.map { layout.doLayout(it) }).each {
            it.contains(fakeRequestId)
        }
    }

    @Test
    fun testLogFilterIncludesArbitrary() {
        val events = mutableListOf<IAccessEvent>()
        val factory = FilteredLogbackAccessRequestLogFactory()
        factory.appenders =
            ImmutableList.of(
                AppenderFactory<IAccessEvent> {
                        _,
                        _,
                        _,
                        _,
                        _,
                    ->
                    object : AppenderBase<IAccessEvent>() {
                        override fun append(eventObject: IAccessEvent) {
                            events.add(eventObject)
                        }
                    }.also { it.start() }
                },
            )
        val logger = factory.build("test")
        val mockRequest = LeakyMock.niceMock<Request>()
        val mockResponse = LeakyMock.niceMock<Response>()
        EasyMock.replay(mockRequest, mockResponse)
        logger.log(mockRequest, mockResponse)
        assertThat(events).isNotEmpty()
    }
}
