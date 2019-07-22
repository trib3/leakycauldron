package com.trib3.server.filters

import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isFailure
import assertk.assertions.isFalse
import assertk.assertions.isSuccess
import assertk.assertions.isTrue
import org.easymock.EasyMock
import org.testng.annotations.Test
import java.util.Base64
import javax.servlet.FilterConfig
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class AdminAuthFilterTest {
    @Test
    fun testUnconfiguredFilter() {
        val mockRequest = EasyMock.niceMock<HttpServletRequest>(HttpServletRequest::class.java)
        val mockResponse = EasyMock.niceMock<HttpServletResponse>(HttpServletResponse::class.java)
        val filter = AdminAuthFilter()
        filter.init(null)
        var proceeded = false
        filter.doFilter(mockRequest, mockResponse) { _, _ ->
            proceeded = true
        }
        assertThat(proceeded).isTrue()
        filter.destroy()
    }

    @Test
    fun testTokenConfiguredFilterGoodPass() {
        val base64 = Base64.getEncoder()
        val mockRequest = EasyMock.niceMock<HttpServletRequest>(HttpServletRequest::class.java)
        val mockResponse = EasyMock.niceMock<HttpServletResponse>(HttpServletResponse::class.java)
        val mockFilterConfig = EasyMock.niceMock<FilterConfig>(FilterConfig::class.java)
        val base64pass = String(base64.encode("abc:123".toByteArray()))
        EasyMock.expect(mockRequest.getHeader(EasyMock.eq("Authorization")))
            .andReturn("Basic $base64pass").anyTimes()
        EasyMock.expect(mockFilterConfig.getInitParameter(EasyMock.eq("token"))).andReturn("123").anyTimes()
        EasyMock.replay(mockRequest, mockResponse, mockFilterConfig)
        val filter = AdminAuthFilter()
        filter.init(mockFilterConfig)
        var proceeded = false
        assertThat {
            filter.doFilter(mockRequest, mockResponse) { _, _ ->
                proceeded = true
            }
        }.isSuccess()
        assertThat(proceeded).isTrue()
    }

    @Test
    fun testTokenConfiguredFilterBadPassCustomRealm() {
        val base64 = Base64.getEncoder()
        val mockRequest = EasyMock.niceMock<HttpServletRequest>(HttpServletRequest::class.java)
        val mockResponse = EasyMock.niceMock<HttpServletResponse>(HttpServletResponse::class.java)
        EasyMock.expect(mockResponse.sendError(EasyMock.eq(HttpServletResponse.SC_UNAUTHORIZED))).once()
        EasyMock.expect(
            mockResponse.setHeader(
                EasyMock.eq("WWW-Authenticate"),
                EasyMock.eq("Basic realm=\"trib3\"")
            )
        ).once()
        val mockFilterConfig = EasyMock.niceMock<FilterConfig>(FilterConfig::class.java)
        val base64pass = String(base64.encode("abc:456".toByteArray()))
        EasyMock.expect(mockRequest.getHeader(EasyMock.eq("Authorization")))
            .andReturn("Basic $base64pass").anyTimes()
        EasyMock.expect(mockFilterConfig.getInitParameter(EasyMock.eq("token"))).andReturn("123").anyTimes()
        EasyMock.expect(mockFilterConfig.getInitParameter(EasyMock.eq("realm"))).andReturn("trib3").anyTimes()
        EasyMock.replay(mockRequest, mockResponse, mockFilterConfig)
        val filter = AdminAuthFilter()
        filter.init(mockFilterConfig)
        var proceeded = false
        assertThat {
            filter.doFilter(mockRequest, mockResponse) { _, _ ->
                proceeded = true
            }
        }.isFailure().hasMessage("Invalid credentials")

        assertThat(proceeded).isFalse()
        EasyMock.verify(mockResponse)
    }

    @Test
    fun testTokenConfiguredFilterBadSchemeDefaultRealm() {
        val mockRequest = EasyMock.niceMock<HttpServletRequest>(HttpServletRequest::class.java)
        val mockResponse = EasyMock.niceMock<HttpServletResponse>(HttpServletResponse::class.java)
        EasyMock.expect(mockResponse.sendError(EasyMock.eq(HttpServletResponse.SC_UNAUTHORIZED))).once()
        EasyMock.expect(
            mockResponse.setHeader(
                EasyMock.eq("WWW-Authenticate"),
                EasyMock.eq("Basic realm=\"Realm\"")
            )
        ).once()
        val mockFilterConfig = EasyMock.niceMock<FilterConfig>(FilterConfig::class.java)
        EasyMock.expect(mockRequest.getHeader(EasyMock.eq("Authorization")))
            .andReturn("Raw abc:1213").anyTimes()
        EasyMock.expect(mockFilterConfig.getInitParameter(EasyMock.eq("token"))).andReturn("123").anyTimes()
        EasyMock.replay(mockRequest, mockResponse, mockFilterConfig)
        val filter = AdminAuthFilter()
        filter.init(mockFilterConfig)
        var proceeded = false
        assertThat {
            filter.doFilter(mockRequest, mockResponse) { _, _ ->
                proceeded = true
            }
        }.isFailure().hasMessage("Invalid credentials")
        assertThat(proceeded).isFalse()
        EasyMock.verify(mockResponse)
    }

    @Test
    fun testTokenConfiguredFilterNoPassDefaultRealm() {
        val mockRequest = EasyMock.niceMock<HttpServletRequest>(HttpServletRequest::class.java)
        val mockResponse = EasyMock.niceMock<HttpServletResponse>(HttpServletResponse::class.java)
        EasyMock.expect(mockResponse.sendError(EasyMock.eq(HttpServletResponse.SC_UNAUTHORIZED))).once()
        EasyMock.expect(
            mockResponse.setHeader(
                EasyMock.eq("WWW-Authenticate"),
                EasyMock.eq("Basic realm=\"Realm\"")
            )
        ).once()
        val mockFilterConfig = EasyMock.niceMock<FilterConfig>(FilterConfig::class.java)
        EasyMock.expect(mockFilterConfig.getInitParameter(EasyMock.eq("token"))).andReturn("123").anyTimes()
        EasyMock.replay(mockRequest, mockResponse, mockFilterConfig)
        val filter = AdminAuthFilter()
        filter.init(mockFilterConfig)
        var proceeded = false
        assertThat {
            filter.doFilter(mockRequest, mockResponse) { _, _ ->
                proceeded = true
            }
        }.isFailure().hasMessage("Invalid credentials")
        assertThat(proceeded).isFalse()
        EasyMock.verify(mockResponse)
    }
}
