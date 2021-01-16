package com.trib3.graphql.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.modules.DataLoaderRegistryFactory
import com.trib3.graphql.modules.GraphQLWebSocketAuthenticator
import graphql.GraphQL
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import javax.annotation.Nullable
import javax.inject.Inject
import javax.ws.rs.container.ContainerRequestContext

interface GraphQLContextWebSocketCreatorFactory {
    fun getCreator(containerRequestContext: ContainerRequestContext): WebSocketCreator
}

/**
 * Factory for getting a [GraphQLWebSocketCreator] for a given [context]
 */
class GraphQLWebSocketCreatorFactory
@Inject constructor(
    private val graphQL: GraphQL,
    private val objectMapper: ObjectMapper,
    private val graphQLConfig: GraphQLConfig,
    @Nullable private val dataLoaderRegistryFactory: DataLoaderRegistryFactory? = null,
    @Nullable private val graphQLWebSocketAuthenticator: GraphQLWebSocketAuthenticator? = null
) : GraphQLContextWebSocketCreatorFactory {

    override fun getCreator(containerRequestContext: ContainerRequestContext): WebSocketCreator {
        return GraphQLWebSocketCreator(
            graphQL,
            objectMapper,
            graphQLConfig,
            containerRequestContext,
            dataLoaderRegistryFactory,
            graphQLWebSocketAuthenticator
        )
    }
}
