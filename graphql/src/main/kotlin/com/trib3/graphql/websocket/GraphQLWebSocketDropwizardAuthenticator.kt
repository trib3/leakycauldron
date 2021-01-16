package com.trib3.graphql.websocket

import com.trib3.graphql.modules.GraphQLWebSocketAuthenticator
import io.dropwizard.auth.AuthFilter
import java.security.Principal
import javax.annotation.Nullable
import javax.inject.Inject
import javax.ws.rs.container.ContainerRequestContext

/**
 * A [GraphQLWebSocketAuthenticator] that delegates authentication
 * to a Dropwizard [AuthFilter] from the Guice injector.
 */
class GraphQLWebSocketDropwizardAuthenticator @Inject constructor(
    @Nullable val authFilter: AuthFilter<*, *>?
) : GraphQLWebSocketAuthenticator {
    override fun invoke(containerRequestContext: ContainerRequestContext): Principal? {
        return runCatching {
            authFilter?.filter(containerRequestContext)
            containerRequestContext.securityContext?.userPrincipal
        }.getOrNull()
    }
}
