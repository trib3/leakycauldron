package com.trib3.graphql.websocket

import com.expediagroup.graphql.dataloader.KotlinDataLoaderRegistryFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.modules.GraphQLWebSocketAuthenticator
import graphql.GraphQL
import jakarta.inject.Inject
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.SecurityContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.eclipse.jetty.websocket.core.server.ServerUpgradeRequest
import org.eclipse.jetty.websocket.core.server.ServerUpgradeResponse
import org.eclipse.jetty.websocket.core.server.WebSocketCreator
import org.glassfish.jersey.internal.MapPropertiesDelegate
import org.glassfish.jersey.server.ContainerRequest
import java.security.Principal
import javax.annotation.Nullable

/**
 * [WebSocketCreator] that creates a [GraphQLWebSocketAdapter], a [Channel] that gets sent
 * WebSocket API events, and a [GraphQLWebSocketConsumer] to consume that channel.
 */
class GraphQLWebSocketCreator(
    val graphQL: GraphQL,
    val objectMapper: ObjectMapper,
    val graphQLConfig: GraphQLConfig,
    private val dataLoaderRegistryFactory: KotlinDataLoaderRegistryFactory? = null,
    private val graphQLWebSocketAuthenticator: GraphQLWebSocketAuthenticator? = null,
    private val dispatcher: CoroutineDispatcher,
) : WebSocketCreator {
    @Inject
    constructor(
        graphQL: GraphQL,
        objectMapper: ObjectMapper,
        graphQLConfig: GraphQLConfig,
        @Nullable dataLoaderRegistryFactory: KotlinDataLoaderRegistryFactory? = null,
        @Nullable graphQLWebSocketAuthenticator: GraphQLWebSocketAuthenticator? = null,
    ) : this(
        graphQL,
        objectMapper,
        graphQLConfig,
        dataLoaderRegistryFactory,
        graphQLWebSocketAuthenticator,
        Dispatchers.Default,
    )

    /**
     * Create the [GraphQLWebSocketAdapter] and its [Channel], and launch a [GraphQLWebSocketConsumer] coroutine
     * to consume the events.  Also set the appropriate subprotocol in our upgrade response.
     */
    override fun createWebSocket(req: ServerUpgradeRequest, resp: ServerUpgradeResponse): Any {
        val containerRequestContext = convertToRequestContext(req)
        val subProtocol = if (req.hasSubProtocol(GraphQLWebSocketSubProtocol.GRAPHQL_WS_PROTOCOL.subProtocol)) {
            GraphQLWebSocketSubProtocol.GRAPHQL_WS_PROTOCOL
        } else {
            GraphQLWebSocketSubProtocol.APOLLO_PROTOCOL
        }
        resp.acceptedSubProtocol = subProtocol.subProtocol
        val channel = Channel<OperationMessage<*>>()
        val adapter = GraphQLWebSocketAdapter(subProtocol, channel, objectMapper)
        val consumer =
            GraphQLWebSocketConsumer(
                graphQL,
                graphQLConfig,
                containerRequestContext,
                channel,
                adapter,
                dispatcher,
                dataLoaderRegistryFactory,
                graphQLWebSocketAuthenticator,
            )
        adapter.launch {
            consumer.consume(this)
        }
        return adapter
    }

    /**
     * Convert a [ServerUpgradeRequest] into a [ContainerRequestContext] which is what we use
     * for doing auth in the [GraphQLWebSocketConsumer].
     */
    internal fun convertToRequestContext(req: ServerUpgradeRequest): ContainerRequestContext {
        val containerRequestContext = ContainerRequest(
            req.requestURI,
            req.requestURI,
            req.method,
            object : SecurityContext {
                override fun getUserPrincipal(): Principal? {
                    return req.userPrincipal
                }

                override fun isUserInRole(role: String?): Boolean {
                    return req.isUserInRole(role)
                }

                override fun isSecure(): Boolean {
                    return req.isSecure
                }

                override fun getAuthenticationScheme(): String {
                    return req.httpServletRequest.authType
                }
            },
            MapPropertiesDelegate(emptyMap()),
            null,
        )
        // transfer HTTP headers
        req.headersMap?.forEach {
            containerRequestContext.headers(it.key, it.value)
        }
        return containerRequestContext
    }
}
