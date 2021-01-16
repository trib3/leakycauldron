package com.trib3.graphql.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.modules.DataLoaderRegistryFactory
import com.trib3.graphql.modules.GraphQLWebSocketAuthenticator
import graphql.GraphQL
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
    private val dataLoaderRegistryFactory: DataLoaderRegistryFactory? = null,
    private val graphQLWebSocketAuthenticator: GraphQLWebSocketAuthenticator? = null
) : WebSocketCreator {
    /**
     * Create the [GraphQLWebSocketAdapter]and its [Channel], and launch a [GraphQLWebSocketConsumer] coroutine
     * to consume the events.  Also set the graphql-ws subprotocol in our upgrade response.
     */
    override fun createWebSocket(req: ServletUpgradeRequest, resp: ServletUpgradeResponse): Any {
        resp.acceptedSubProtocol = graphQLConfig.webSocketSubProtocol
        val channel = Channel<OperationMessage<*>>()
        val adapter = GraphQLWebSocketAdapter(channel, objectMapper)
        val consumer =
            GraphQLWebSocketConsumer(
                graphQL,
                graphQLConfig,
                containerRequestContext,
                channel,
                adapter,
                Dispatchers.Default,
                dataLoaderRegistryFactory,
                graphQLWebSocketAuthenticator
            )
        adapter.launch {
            consumer.consume(this)
        }
        return adapter
    }
}
