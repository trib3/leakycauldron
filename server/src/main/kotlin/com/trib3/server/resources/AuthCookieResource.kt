package com.trib3.server.resources

import com.codahale.metrics.annotation.Timed
import com.trib3.server.config.TribeApplicationConfig
import org.eclipse.jetty.http.HttpStatus
import javax.annotation.security.PermitAll
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context
import javax.ws.rs.core.NewCookie
import javax.ws.rs.core.Response

@Path("/")
class AuthCookieResource @Inject constructor(
    val appConfig: TribeApplicationConfig
) {
    /**
     * Create the cookie name based on the configured application name
     */
    private fun getCookieName(): String {
        val formattedAppName = appConfig.appName.toUpperCase().replace(' ', '_')
        return "${formattedAppName}_AUTHORIZATION"
    }

    /**
     * Set authorization credentials in the HTTP headers as a session-lifetime,
     * HTTP-only cookie.
     *
     * This allows, eg, a JS websocket client to authenticate to the service
     * via BasicAuthentication, make a request to this endpoint, and then
     * initiate a cookie-authenticated websocket connection without js ever
     * having access to a credential
     */
    @GET
    @Path("/auth_cookie")
    @Timed
    @PermitAll
    fun setAuthCookie(
        @Context containerRequestContext: ContainerRequestContext
    ): Response {
        val authHeaderSplit = containerRequestContext
            .getHeaderString("Authorization")
            ?.split(' ', limit = 2)
        val authToken = authHeaderSplit?.last()
        return Response.status(HttpStatus.NO_CONTENT_204).let { builder ->
            if (authToken != null) {
                builder.cookie(
                    NewCookie(
                        getCookieName(),
                        authToken,
                        "/app",
                        null,
                        1,
                        null,
                        NewCookie.DEFAULT_MAX_AGE, // expire in 30 days
                        null,
                        containerRequestContext.securityContext.isSecure,
                        true
                    )
                )
            } else {
                builder
            }
        }.build()
    }
}
