package com.trib3.server.graphql

import com.fasterxml.jackson.databind.ObjectMapper
import graphql.GraphQL
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import javax.annotation.Nullable
import javax.inject.Inject

/**
 * [WebSocketCreator] that creates as [GraphQLWebSocket], passing injected [GraphQL] and [ObjectMapper] instances
 */
class GraphQLWebSocketCreator
@Inject constructor(
    @Nullable val graphQL: GraphQL?,
    val objectMapper: ObjectMapper,
    val graphQLConfig: GraphQLConfig
) : WebSocketCreator {
    override fun createWebSocket(req: ServletUpgradeRequest, resp: ServletUpgradeResponse): Any {
        resp.acceptedSubProtocol = graphQLConfig.webSocketSubProtocol
        return GraphQLWebSocket(graphQL, objectMapper, graphQLConfig.keepAliveIntervalSeconds)
    }
}
