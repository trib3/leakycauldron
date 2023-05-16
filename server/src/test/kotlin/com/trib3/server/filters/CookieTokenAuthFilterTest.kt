package com.trib3.server.filters

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isSuccess
import assertk.assertions.messageContains
import com.trib3.testing.LeakyMock
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Cookie
import jakarta.ws.rs.core.SecurityContext
import org.easymock.EasyMock
import org.testng.annotations.Test
import java.security.Principal
import java.util.Optional

data class Session(val email: String) : Principal {
    override fun getName(): String {
        return email
    }
}

class CookieTokenAuthFilterTest {
    val filter = CookieTokenAuthFilter.Builder<Session>("cookieName")
        .setAuthenticator {
            if (it == "value") {
                Optional.of(Session("blah@blee.com"))
            } else {
                Optional.empty()
            }
        }
        .buildAuthFilter()

    @Test
    fun testBuilder() {
        assertThat(filter.cookieName).isEqualTo("cookieName")
    }

    @Test
    fun testAuthed() {
        val mockContext = LeakyMock.niceMock<ContainerRequestContext>()
        EasyMock.expect(mockContext.cookies).andReturn(
            mapOf("cookieName" to Cookie("cookieName", "value")),
        )
        val captureContext = EasyMock.newCapture<SecurityContext>()
        EasyMock.expect(mockContext.setSecurityContext(EasyMock.capture(captureContext)))
        EasyMock.replay(mockContext)
        assertThat {
            filter.filter(mockContext)
        }.isSuccess()
        assertThat(captureContext.value.userPrincipal).isInstanceOf(Session::class)
        assertThat((captureContext.value.userPrincipal as Session).email).isEqualTo("blah@blee.com")
        EasyMock.verify(mockContext)
    }

    @Test
    fun testBadAuth() {
        val mockContext = LeakyMock.niceMock<ContainerRequestContext>()
        EasyMock.expect(mockContext.cookies).andReturn(
            mapOf("cookieName" to Cookie("cookieName", "badvalue")),
        )
        EasyMock.replay(mockContext)
        assertThat {
            filter.filter(mockContext)
        }.isFailure().isInstanceOf(WebApplicationException::class).messageContains("401 Unauthorized")
        EasyMock.verify(mockContext)
    }

    @Test
    fun testWrongCookieAuth() {
        val mockContext = LeakyMock.niceMock<ContainerRequestContext>()
        EasyMock.expect(mockContext.cookies).andReturn(
            mapOf("wrongCookieName" to Cookie("wrongCookieName", "value")),
        )
        EasyMock.replay(mockContext)
        assertThat {
            filter.filter(mockContext)
        }.isFailure().isInstanceOf(WebApplicationException::class).messageContains("401 Unauthorized")
        EasyMock.verify(mockContext)
    }

    @Test
    fun testUnauthed() {
        val mockContext = LeakyMock.niceMock<ContainerRequestContext>()
        EasyMock.expect(mockContext.cookies).andReturn(mapOf())
        EasyMock.replay(mockContext)
        assertThat {
            filter.filter(mockContext)
        }.isFailure().isInstanceOf(WebApplicationException::class).messageContains("401 Unauthorized")
        EasyMock.verify(mockContext)
    }
}
