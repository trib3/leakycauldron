package com.trib3.graphql.execution

import com.trib3.graphql.modules.DataLoaderRegistryFactory
import com.trib3.server.runIf
import graphql.ExecutionInput

/**
 * A generic GraphQL Request object that includes query, variable, operationName and extensions components
 */
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any>?,
    val operationName: String?,
    val extensions: Map<String, String> = mapOf()
)

/**
 * Extension function to convert a [GraphQLRequest] to an [ExecutionInput]
 */
fun GraphQLRequest.toExecutionInput(
    contextMap: Map<*, Any>? = null,
    dataLoaderRegistryFactory: DataLoaderRegistryFactory? = null
): ExecutionInput {
    return ExecutionInput.newExecutionInput()
        .query(this.query)
        .variables(this.variables.orEmpty())
        .operationName(operationName)
        .runIf(contextMap != null) {
            graphQLContext(contextMap)
        }.let { builder ->
            dataLoaderRegistryFactory?.let { factory ->
                builder.dataLoaderRegistry(factory(this, contextMap))
            } ?: builder
        }.build()
}
