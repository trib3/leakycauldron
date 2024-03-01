package com.trib3.graphql.websocket

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.dropwizard.auth.AuthFilter
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.SecurityContext
import org.glassfish.jersey.internal.MapPropertiesDelegate
import org.glassfish.jersey.server.ContainerRequest
import org.testng.annotations.Test
import java.security.Principal

data class TestPrincipal(val _name: String) : Principal {
    override fun getName(): String {
        return _name
    }
}

class TestAuthenticator : AuthFilter<String, TestPrincipal>() {
    override fun filter(requestContext: ContainerRequestContext) {
        val principal =
            when (requestContext.getHeaderString("user")) {
                "bill" -> TestPrincipal("billy")
                "bob" -> TestPrincipal("bobby")
                "evil" -> throw IllegalArgumentException("bad user")
                else -> null
            }
        requestContext.securityContext =
            object : SecurityContext {
                override fun getUserPrincipal(): Principal? {
                    return principal
                }

                override fun isUserInRole(role: String?): Boolean {
                    return false
                }

                override fun isSecure(): Boolean {
                    return false
                }

                override fun getAuthenticationScheme(): String {
                    return "test"
                }
            }
    }
}

class GraphQLWebSocketDropwizardAuthenticatorTest {
    private fun getContext(user: String?): ContainerRequestContext {
        return ContainerRequest(
            null,
            null,
            null,
            null,
            MapPropertiesDelegate(emptyMap()),
            null,
        ).apply {
            header("user", user)
        }
    }

    @Test
    fun testAuth() {
        val authenticator = GraphQLWebSocketDropwizardAuthenticator(TestAuthenticator())
        assertThat(authenticator.authFilter).isNotNull()
        assertThat(authenticator.invoke(getContext("bob"))).isEqualTo(TestPrincipal("bobby"))
        assertThat(authenticator.invoke(getContext("bill"))).isEqualTo(TestPrincipal("billy"))
        assertThat(authenticator.invoke(getContext("sam"))).isNull()
        assertThat(authenticator.invoke(getContext("evil"))).isNull()
    }

    @Test
    fun testNoAuth() {
        val authenticator = GraphQLWebSocketDropwizardAuthenticator(null)
        assertThat(authenticator.authFilter).isNull()
        assertThat(authenticator.invoke(getContext("bob"))).isNull()
        assertThat(authenticator.invoke(getContext("bill"))).isNull()
        assertThat(authenticator.invoke(getContext("sam"))).isNull()
        assertThat(authenticator.invoke(getContext("evil"))).isNull()
    }
}
