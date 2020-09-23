package com.trib3.server.filters

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.trib3.testing.LeakyMock
import org.easymock.EasyMock
import org.slf4j.MDC
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.util.UUID
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class RequestIdFilterTest {
    val filter = RequestIdFilter()

    @BeforeClass
    fun setUp() {
        filter.init(null)
    }

    @AfterClass
    fun tearDown() {
        filter.destroy()
    }

    @Test
    fun testLoggingFilter() {
        val mockRequest = LeakyMock.mock<HttpServletRequest>()
        val mockResponse = LeakyMock.mock<HttpServletResponse>()
        EasyMock.expect(mockRequest.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).andReturn(null)
        EasyMock.expect(mockResponse.setHeader(EasyMock.eq(RequestIdFilter.REQUEST_ID_HEADER), LeakyMock.anyString()))
            .once()
        EasyMock.replay(mockRequest, mockResponse)
        filter.doFilter(mockRequest, mockResponse) { _, _ ->
            assertThat(MDC.get(RequestIdFilter.REQUEST_ID_KEY)).isNotEmpty()
        }
        EasyMock.verify(mockResponse)
    }

    @Test
    fun testLoggingFilterWithClientRequestId() {
        val requestId = UUID.randomUUID().toString()
        val mockRequest = LeakyMock.mock<HttpServletRequest>()
        val mockResponse = LeakyMock.mock<HttpServletResponse>()
        EasyMock.expect(mockRequest.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).andReturn(requestId)
        EasyMock.expect(mockResponse.setHeader(RequestIdFilter.REQUEST_ID_HEADER, requestId))
            .once()
        EasyMock.replay(mockRequest, mockResponse)
        filter.doFilter(mockRequest, mockResponse) { _, _ ->
            assertThat(MDC.get(RequestIdFilter.REQUEST_ID_KEY)).isNotEmpty()
        }
        EasyMock.verify(mockResponse)
    }

    @Test
    fun testLoggingFilterWithInvalidClientRequestId() {
        val requestId = "blahblahblah"
        val mockRequest = LeakyMock.mock<HttpServletRequest>()
        val mockResponse = LeakyMock.mock<HttpServletResponse>()
        EasyMock.expect(mockRequest.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).andReturn(requestId)
        EasyMock.expect(
            mockResponse.setHeader(
                EasyMock.eq(RequestIdFilter.REQUEST_ID_HEADER),
                EasyMock.not(EasyMock.eq(requestId))
            )
        ).once()
        EasyMock.replay(mockRequest, mockResponse)
        filter.doFilter(mockRequest, mockResponse) { _, _ ->
            assertThat(MDC.get(RequestIdFilter.REQUEST_ID_KEY)).isNotEmpty()
        }
        EasyMock.verify(mockResponse)
    }

    @Test
    fun testLoggingFilterWithMDCSet() {
        val requestId = UUID.randomUUID().toString()
        MDC.put(RequestIdFilter.REQUEST_ID_KEY, requestId)
        try {
            val mockRequest = LeakyMock.mock<HttpServletRequest>()
            val mockResponse = LeakyMock.mock<HttpServletResponse>()
            EasyMock.expect(mockRequest.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).andReturn(null)
            EasyMock.expect(
                mockResponse.setHeader(
                    EasyMock.eq(RequestIdFilter.REQUEST_ID_HEADER),
                    EasyMock.eq(requestId)
                )
            ).once()
            EasyMock.replay(mockRequest, mockResponse)
            filter.doFilter(mockRequest, mockResponse) { _, _ ->
                assertThat(MDC.get(RequestIdFilter.REQUEST_ID_KEY)).isEqualTo(requestId)
            }
            EasyMock.verify(mockResponse)
        } finally {
            MDC.remove(RequestIdFilter.REQUEST_ID_KEY)
        }
    }

    @Test
    fun testLoggingFilterWithRawServletReqResp() {
        val mockRequest = LeakyMock.mock<ServletRequest>()
        val mockResponse = LeakyMock.mock<ServletResponse>()
        EasyMock.replay(mockRequest, mockResponse)
        filter.doFilter(mockRequest, mockResponse) { _, _ ->
            assertThat(MDC.get(RequestIdFilter.REQUEST_ID_KEY)).isNotEmpty()
        }
        EasyMock.verify(mockResponse)
    }

    @Test
    fun testCompanionMethods() {
        val testRequestId = "TESTTEST"
        RequestIdFilter.withRequestId(testRequestId) {
            assertThat(RequestIdFilter.getRequestId()).isEqualTo(testRequestId)
        }
        assertThat(RequestIdFilter.getRequestId()).isNull()
        val set = RequestIdFilter.createRequestId(testRequestId)
        assertThat(set).isTrue()
        try {
            assertThat(RequestIdFilter.getRequestId()).isEqualTo(testRequestId)
        } finally {
            RequestIdFilter.clearRequestId()
        }
        assertThat(RequestIdFilter.getRequestId()).isNull()
    }
}
