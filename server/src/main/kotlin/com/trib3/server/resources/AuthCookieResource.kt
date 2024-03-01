package com.trib3.server.resources

import com.codahale.metrics.annotation.Timed
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.runIf
import jakarta.annotation.security.PermitAll
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.NewCookie
import jakarta.ws.rs.core.Response
import org.eclipse.jetty.http.HttpStatus

@Path("/")
class AuthCookieResource
    @Inject
    constructor(
        val appConfig: TribeApplicationConfig,
    ) {
        /**
         * Create the cookie name based on the configured application name
         */
        private fun getCookieName(): String {
            val formattedAppName = appConfig.appName.uppercase().replace(' ', '_')
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
            @Context containerRequestContext: ContainerRequestContext,
        ): Response {
            val authHeaderSplit =
                containerRequestContext
                    .getHeaderString("Authorization")
                    ?.split(' ', limit = 2)
            val authToken = authHeaderSplit?.last()
            return Response.status(HttpStatus.NO_CONTENT_204)
                .runIf(authToken != null) {
                    cookie(
                        NewCookie.Builder(getCookieName())
                            .value(authToken)
                            .path("/app")
                            .secure(containerRequestContext.securityContext.isSecure)
                            .httpOnly(true)
                            .build(),
                    )
                }.build()
        }
    }
