package com.trib3.graphql.websocket

import com.expediagroup.graphql.execution.GraphQLContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.graphql.GraphQLConfig
import graphql.GraphQL
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import javax.inject.Inject

interface GraphQLContextWebSocketCreatorFactory {
    fun getCreator(context: GraphQLContext): WebSocketCreator
}

/**
 * Factory for getting a [GraphQLWebSocketCreator] for a given [context]
 */
class GraphQLWebSocketCreatorFactory
@Inject constructor(
    private val graphQL: GraphQL,
    private val objectMapper: ObjectMapper,
    private val graphQLConfig: GraphQLConfig
) : GraphQLContextWebSocketCreatorFactory {
    override fun getCreator(context: GraphQLContext): WebSocketCreator {
        return GraphQLWebSocketCreator(
            graphQL,
            objectMapper,
            graphQLConfig,
            context
        )
    }
}
