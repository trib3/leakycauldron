package com.trib3.server.config.dropwizard

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.each
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import ch.qos.logback.access.common.spi.IAccessEvent
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.AppenderBase
import com.google.common.collect.ImmutableList
import com.trib3.config.ConfigLoader
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.testing.LeakyMock
import com.trib3.testing.server.TestServletContextRequest
import io.dropwizard.logging.common.AppenderFactory
import jakarta.servlet.http.HttpServletResponse
import org.easymock.EasyMock
import org.eclipse.jetty.ee10.servlet.ServletChannel
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.http.HttpField
import org.eclipse.jetty.http.HttpFields
import org.eclipse.jetty.http.HttpURI
import org.eclipse.jetty.server.ConnectionMetaData
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.slf4j.LoggerFactory
import org.testng.annotations.Test
import java.util.TimeZone

// The filter treats a request as "fast" when elapsedTime < 200ms (FAST_RESPONSE_TIME).
// elapsedTime is computed during logger.log() as (event time "now" - request start), where the
// request start is derived from the mocked Request.headersNanoTime.  These offsets are applied with
// EasyMock.andAnswer (NOT andReturn) so that System.nanoTime() is sampled when the event reads
// headersNanoTime during log(), i.e. at the same instant as the "now" end timestamp.  Using
// andReturn would sample nanoTime at mock-setup time instead, letting any latency before log()
// (e.g. cold-start class loading on a constrained CI machine) leak into elapsedTime and flip a
// fast response into a slow one -- the cause of a flaky CI failure in this test.
private const val FAST_RESPONSE_NANOS = 100_000_000L // simulates a 100ms (< 200ms) fast response
private const val SLOW_RESPONSE_NANOS = 300_000_000L // simulates a 300ms (> 200ms) slow response

class FilteredRequestLogTest {
    private val appConfig = TribeApplicationConfig(ConfigLoader())
    private val customAppConfig = TribeApplicationConfig(ConfigLoader("appContextPathTestCase"))

    @Test
    fun testLogFilterExcludesPingAndPrometheus() {
        for (path in setOf("/app/ping", "/admin/prometheus")) {
            val events = mutableListOf<IAccessEvent>()
            val factory = FilteredLogbackAccessRequestLogFactory(appConfig)
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
            val connectionMetaData = LeakyMock.niceMock<ConnectionMetaData>()
            EasyMock.expect(mockRequest.httpURI).andReturn(HttpURI.from(path)).anyTimes()
            EasyMock
                .expect(
                    mockRequest.headersNanoTime,
                ).andAnswer { System.nanoTime() - FAST_RESPONSE_NANOS }
                .anyTimes()
            EasyMock.expect(mockResponse.status).andReturn(HttpServletResponse.SC_OK).anyTimes()
            EasyMock.expect(connectionMetaData.httpConfiguration).andReturn(HttpConfiguration()).anyTimes()
            EasyMock.expect(connectionMetaData.protocol).andReturn("HTTP/1.1").anyTimes()
            EasyMock.replay(mockRequest, mockResponse, connectionMetaData)
            val handler = ServletContextHandler()
            val channel = ServletChannel(handler, connectionMetaData)
            val testRequest = TestServletContextRequest(handler, channel, mockRequest, mockResponse)

            logger.log(testRequest, mockResponse)
            assertThat(events).isEmpty()
        }
    }

    @Test
    fun testLogFilterExcludesPingForCustomAppContextPath() {
        val events = mutableListOf<IAccessEvent>()
        val factory = FilteredLogbackAccessRequestLogFactory(customAppConfig)
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
        val connectionMetaData = LeakyMock.niceMock<ConnectionMetaData>()
        // `/custom/ping` should be filtered with custom context path.
        EasyMock.expect(mockRequest.httpURI).andReturn(HttpURI.from("/custom/ping")).anyTimes()
        EasyMock.expect(mockRequest.headersNanoTime).andAnswer { System.nanoTime() - FAST_RESPONSE_NANOS }.anyTimes()
        EasyMock.expect(mockResponse.status).andReturn(HttpServletResponse.SC_OK).anyTimes()
        EasyMock.expect(connectionMetaData.httpConfiguration).andReturn(HttpConfiguration()).anyTimes()
        EasyMock.expect(connectionMetaData.protocol).andReturn("HTTP/1.1").anyTimes()
        EasyMock.replay(mockRequest, mockResponse, connectionMetaData)
        val handler = ServletContextHandler()
        val channel = ServletChannel(handler, connectionMetaData)
        val testRequest = TestServletContextRequest(handler, channel, mockRequest, mockResponse)

        logger.log(testRequest, mockResponse)
        assertThat(events).isEmpty()
    }

    @Test
    fun testLogFilterIncludesSlowPing() {
        val events = mutableListOf<IAccessEvent>()
        val factory = FilteredLogbackAccessRequestLogFactory(appConfig)
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
        val connectionMetaData = LeakyMock.niceMock<ConnectionMetaData>()
        EasyMock.expect(mockRequest.httpURI).andReturn(HttpURI.from("/app/ping")).anyTimes()
        EasyMock.expect(mockRequest.headersNanoTime).andAnswer { System.nanoTime() - SLOW_RESPONSE_NANOS }.anyTimes()
        EasyMock.expect(mockResponse.status).andReturn(HttpServletResponse.SC_OK).anyTimes()
        EasyMock.expect(connectionMetaData.httpConfiguration).andReturn(HttpConfiguration()).anyTimes()
        EasyMock.expect(connectionMetaData.protocol).andReturn("HTTP/1.1").anyTimes()
        EasyMock.replay(mockRequest, mockResponse, connectionMetaData)
        val handler = ServletContextHandler()
        val channel = ServletChannel(handler, connectionMetaData)
        val testRequest = TestServletContextRequest(handler, channel, mockRequest, mockResponse)
        logger.log(testRequest, mockResponse)
        assertThat(events).isNotEmpty()
    }

    @Test
    fun testLogFilterIncludesFailedPing() {
        val events = mutableListOf<IAccessEvent>()
        val factory = FilteredLogbackAccessRequestLogFactory(appConfig)
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

        val fakeRequestId = "f74663ea-4da5-4890-91a7-e63f8d1ba82c"
        val responseHeaders = HttpFields.build(HttpFields.from(HttpField(null, "X-Request-Id", fakeRequestId)))
        val mockRequest = LeakyMock.niceMock<Request>()
        val mockResponse = LeakyMock.niceMock<Response>()
        val connectionMetaData = LeakyMock.niceMock<ConnectionMetaData>()
        EasyMock.expect(mockRequest.httpURI).andReturn(HttpURI.from("/app/ping")).anyTimes()
        EasyMock.expect(mockRequest.headersNanoTime).andAnswer { System.nanoTime() - FAST_RESPONSE_NANOS }.anyTimes()
        EasyMock.expect(mockRequest.headers).andReturn(HttpFields.build()).anyTimes()
        EasyMock.expect(mockResponse.headers).andReturn(responseHeaders).anyTimes()
        EasyMock.expect(mockResponse.status).andReturn(HttpServletResponse.SC_SERVICE_UNAVAILABLE).anyTimes()
        EasyMock.expect(connectionMetaData.httpConfiguration).andReturn(HttpConfiguration()).anyTimes()
        EasyMock.expect(connectionMetaData.protocol).andReturn("HTTP/1.1").anyTimes()
        EasyMock.expect(mockRequest.connectionMetaData).andReturn(connectionMetaData).anyTimes()
        EasyMock.replay(mockRequest, mockResponse, connectionMetaData)
        val handler = ServletContextHandler()
        val channel = ServletChannel(handler, connectionMetaData)
        val testRequest = TestServletContextRequest(handler, channel, mockRequest, mockResponse)

        logger.log(testRequest, mockResponse)
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
        val factory = FilteredLogbackAccessRequestLogFactory(appConfig)
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
        val connectionMetaData = LeakyMock.niceMock<ConnectionMetaData>()
        EasyMock.expect(mockRequest.httpURI).andReturn(HttpURI.from("/app/random/uri")).anyTimes()
        EasyMock.expect(mockRequest.headersNanoTime).andAnswer { System.nanoTime() - FAST_RESPONSE_NANOS }.anyTimes()
        EasyMock.expect(mockResponse.status).andReturn(HttpServletResponse.SC_OK).anyTimes()
        EasyMock.expect(connectionMetaData.httpConfiguration).andReturn(HttpConfiguration()).anyTimes()
        EasyMock.expect(connectionMetaData.protocol).andReturn("HTTP/1.1").anyTimes()
        EasyMock.replay(mockRequest, mockResponse, connectionMetaData)
        val handler = ServletContextHandler()
        val channel = ServletChannel(handler, connectionMetaData)
        val testRequest = TestServletContextRequest(handler, channel, mockRequest, mockResponse)
        logger.log(testRequest, mockResponse)
        assertThat(events).isNotEmpty()
    }
}
