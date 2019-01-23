package com.trib3.server.logging

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
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
        val mockRequest = EasyMock.mock(HttpServletRequest::class.java)
        val mockResponse = EasyMock.mock(HttpServletResponse::class.java)
        EasyMock.expect(mockResponse.setHeader(EasyMock.eq(RequestIdFilter.REQUEST_ID_HEADER), EasyMock.anyString()))
            .once()
        EasyMock.replay(mockRequest, mockResponse)
        filter.doFilter(mockRequest, mockResponse) { _, _ ->
            assert(MDC.get(RequestIdFilter.REQUEST_ID_KEY)).isNotEmpty()
        }
        EasyMock.verify(mockResponse)
    }

    @Test
    fun testLoggingFilterWithMDCSet() {
        val requestId = UUID.randomUUID().toString()
        MDC.put(RequestIdFilter.REQUEST_ID_KEY, requestId)
        try {
            val mockRequest = EasyMock.mock(HttpServletRequest::class.java)
            val mockResponse = EasyMock.mock(HttpServletResponse::class.java)
            EasyMock.expect(
                mockResponse.setHeader(
                    EasyMock.eq(RequestIdFilter.REQUEST_ID_HEADER),
                    EasyMock.eq(requestId)
                )
            ).once()
            EasyMock.replay(mockRequest, mockResponse)
            filter.doFilter(mockRequest, mockResponse) { _, _ ->
                assert(MDC.get(RequestIdFilter.REQUEST_ID_KEY)).isEqualTo(requestId)
            }
            EasyMock.verify(mockResponse)
        } finally {
            MDC.remove(RequestIdFilter.REQUEST_ID_KEY)
        }
    }

    @Test
    fun testLoggingFilterWithRawServletReqResp() {
        val mockRequest = EasyMock.mock(ServletRequest::class.java)
        val mockResponse = EasyMock.mock(ServletResponse::class.java)
        EasyMock.replay(mockRequest, mockResponse)
        filter.doFilter(mockRequest, mockResponse) { _, _ ->
            assert(MDC.get(RequestIdFilter.REQUEST_ID_KEY)).isNotEmpty()
        }
        EasyMock.verify(mockResponse)
    }
}