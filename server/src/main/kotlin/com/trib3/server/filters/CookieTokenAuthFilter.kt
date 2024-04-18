package com.trib3.server.filters

import com.trib3.server.filters.CookieTokenAuthFilter.Builder
import io.dropwizard.auth.AuthFilter
import jakarta.annotation.Priority
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.container.ContainerRequestContext
import java.security.Principal

/**
 * [AuthFilter] that reads a token value out of a cookie and authenticates against the
 * Authenticator / Authorizers that are configured through the [Builder]
 */
@Priority(Priorities.AUTHENTICATION)
class CookieTokenAuthFilter<P : Principal>(val cookieName: String) : AuthFilter<String?, P>() {
    override fun filter(requestContext: ContainerRequestContext) {
        val credentials =
            requestContext.cookies
                .filter { it.key == cookieName }
                .values.map { it.value }.firstOrNull()
        if (!authenticate(requestContext, credentials, "Bearer")) {
            throw WebApplicationException(unauthorizedHandler.buildResponse(prefix, realm))
        }
    }

    /**
     * Builder for [CookieTokenAuthFilter].  The user must specify a [cookieName] to read the token
     * from to begin the building process.  The user must provide an Authenticator<String?, P> to
     * authenticate the cookie and return a [Principal] that corresponds to the cookie token value
     * during the building process.
     */
    class Builder<P : Principal>(private val cookieName: String) :
        AuthFilterBuilder<String?, P, CookieTokenAuthFilter<P>>() {
        override fun newInstance(): CookieTokenAuthFilter<P> {
            return CookieTokenAuthFilter(cookieName)
        }
    }
}
