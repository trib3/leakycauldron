package com.trib3.server.graphql

/**
 * A generic GraphQL Request object that includes query, variable, and operationName components
 */
data class GraphRequest(val query: String, val variables: Map<String, Any>?, val operationName: String?)
