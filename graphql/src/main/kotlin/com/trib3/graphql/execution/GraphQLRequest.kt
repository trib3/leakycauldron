package com.trib3.graphql.execution

/**
 * A generic GraphQL Request object that includes query, variable, and operationName components
 */
data class GraphQLRequest(val query: String, val variables: Map<String, Any>?, val operationName: String?)
