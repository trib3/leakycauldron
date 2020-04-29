package com.trib3.graphql.execution

import com.trib3.graphql.modules.DataLoaderRegistryFactory
import graphql.ExecutionInput

/**
 * A generic GraphQL Request object that includes query, variable, and operationName components
 */
data class GraphQLRequest(val query: String, val variables: Map<String, Any>?, val operationName: String?)

/**
 * Extension function to convert a [GraphQLRequest] to an [ExecutionInput]
 */
fun GraphQLRequest.toExecutionInput(
    context: Any? = null,
    dataLoaderRegistryFactory: DataLoaderRegistryFactory? = null
): ExecutionInput {
    return ExecutionInput.newExecutionInput()
        .query(this.query)
        .variables(this.variables ?: emptyMap())
        .operationName(operationName)
        .let { builder ->
            context?.let { ctx ->
                builder.context(ctx)
            } ?: builder
        }.let { builder ->
            dataLoaderRegistryFactory?.let { factory ->
                builder.dataLoaderRegistry(factory(this, context))
            } ?: builder
        }.build()
}
