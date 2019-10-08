package com.trib3.graphql.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.graphql.GraphQLConfig
import graphql.GraphQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import javax.annotation.Nullable
import javax.inject.Inject

/**
 * [WebSocketCreator] that creates a [GraphQLWebSocketAdapter], a [Channel] that gets sent
 * WebSocket API events, and a [GraphQLWebSocketConsumer] to consume that channel.
 */
class GraphQLWebSocketCreator
@Inject constructor(
    @Nullable val graphQL: GraphQL?,
    val objectMapper: ObjectMapper,
    val graphQLConfig: GraphQLConfig
) : WebSocketCreator {
    /**
     * Create the [GraphQLWebSocketAdapter]and its [Channel], and launch a [GraphQLWebSocketConsumer] coroutine
     * to consume the events.  Also set the graphql-ws subprotocol in our upgrade response.
     */
    override fun createWebSocket(req: ServletUpgradeRequest, resp: ServletUpgradeResponse): Any {
        resp.acceptedSubProtocol = graphQLConfig.webSocketSubProtocol
        val channel = Channel<OperationMessage<*>>()
        val adapter = GraphQLWebSocketAdapter(channel, objectMapper)
        val consumer = GraphQLWebSocketConsumer(graphQL, graphQLConfig, channel, adapter, Dispatchers.IO)
        consumer.launchConsumer()
        return adapter
    }
}
