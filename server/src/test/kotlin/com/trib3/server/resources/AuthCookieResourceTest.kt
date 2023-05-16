package com.trib3.server.resources

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.trib3.config.ConfigLoader
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.testing.LeakyMock
import com.trib3.testing.server.ResourceTestBase
import io.dropwizard.auth.AuthDynamicFeature
import io.dropwizard.auth.basic.BasicCredentialAuthFilter
import io.dropwizard.testing.common.Resource
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.SecurityContext
import org.easymock.EasyMock
import org.eclipse.jetty.http.HttpStatus
import org.testng.annotations.Test
import java.security.Principal
import java.util.Optional

data class UserPrincipal(private val _name: String) : Principal {
    override fun getName(): String {
        return _name
    }
}

class AuthCookieResourceTest : ResourceTestBase<AuthCookieResource>() {
    private val appConfig = TribeApplicationConfig(ConfigLoader())
    override fun getResource(): AuthCookieResource {
        return AuthCookieResource(appConfig)
    }

    override fun buildAdditionalResources(resourceBuilder: Resource.Builder<*>) {
        resourceBuilder.addProvider(
            AuthDynamicFeature(
                BasicCredentialAuthFilter.Builder<UserPrincipal>()
                    .setAuthenticator {
                        if (it.username == "user") {
                            Optional.of(UserPrincipal("bill"))
                        } else {
                            Optional.empty()
                        }
                    }
                    .buildAuthFilter(),
            ),
        )
    }

    @Test
    fun testUnauthorized() {
        val response = resource.target("/auth_cookie").request().get()
        assertThat(response.status).isEqualTo(HttpStatus.UNAUTHORIZED_401)
        assertThat(response.cookies).isEmpty()
    }

    @Test
    fun testBadAuthorization() {
        val response = resource.target("/auth_cookie").request().header("Authorization", "Basic YmxhaDoxMjM0NQ==").get()
        assertThat(response.status).isEqualTo(HttpStatus.UNAUTHORIZED_401)
        assertThat(response.cookies).isEmpty()
    }

    @Test
    fun testGoodAuthorization() {
        val response = resource.target("/auth_cookie").request().header("Authorization", "Basic dXNlcjoxMjM0NQ==").get()
        assertThat(response.status).isEqualTo(HttpStatus.NO_CONTENT_204)
        assertThat(response.cookies["TEST_AUTHORIZATION"]?.name).isEqualTo("TEST_AUTHORIZATION")
        assertThat(response.cookies["TEST_AUTHORIZATION"]?.value).isEqualTo("dXNlcjoxMjM0NQ==")
        assertThat(response.cookies["TEST_AUTHORIZATION"]?.maxAge).isEqualTo(-1) // session cookie
        assertThat(response.cookies["TEST_AUTHORIZATION"]?.isHttpOnly).isNotNull().isTrue()
    }

    @Test
    fun testNullAuthHeaderCase() {
        // test against the resource method directly
        val rawResource = AuthCookieResource(appConfig)
        val mockRequest = LeakyMock.mock<ContainerRequestContext>()
        val mockSecContext = LeakyMock.mock<SecurityContext>()
        EasyMock.expect(mockRequest.getHeaderString("Authorization")).andReturn(null)
        EasyMock.expect(mockRequest.securityContext).andReturn(mockSecContext)
        EasyMock.expect(mockSecContext.isSecure).andReturn(true)
        EasyMock.replay(mockRequest, mockSecContext)
        val response = rawResource.setAuthCookie(mockRequest)
        assertThat(response.status).isEqualTo(HttpStatus.NO_CONTENT_204)
        assertThat(response.cookies).isEmpty()
    }
}
