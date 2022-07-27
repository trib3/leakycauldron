package com.trib3.graphql.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.modules.GraphQLWebSocketAuthenticator
import com.trib3.graphql.modules.KotlinDataLoaderRegistryFactoryProvider
import graphql.GraphQL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import javax.ws.rs.container.ContainerRequestContext

/**
 * [WebSocketCreator] that creates a [GraphQLWebSocketAdapter], a [Channel] that gets sent
 * WebSocket API events, and a [GraphQLWebSocketConsumer] to consume that channel.
 */
class GraphQLWebSocketCreator(
    val graphQL: GraphQL,
    val objectMapper: ObjectMapper,
    val graphQLConfig: GraphQLConfig,
    val containerRequestContext: ContainerRequestContext,
    private val dataLoaderRegistryFactory: KotlinDataLoaderRegistryFactoryProvider? = null,
    private val graphQLWebSocketAuthenticator: GraphQLWebSocketAuthenticator? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : WebSocketCreator {
    /**
     * Create the [GraphQLWebSocketAdapter]and its [Channel], and launch a [GraphQLWebSocketConsumer] coroutine
     * to consume the events.  Also set the appropriate subprotocol in our upgrade response.
     */
    override fun createWebSocket(req: ServletUpgradeRequest, resp: ServletUpgradeResponse): Any {
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
                graphQLWebSocketAuthenticator
            )
        adapter.launch {
            consumer.consume(this)
        }
        return adapter
    }
}
