package com.trib3.graphql.execution

import graphql.ErrorClassification
import graphql.GraphQLError
import graphql.language.SourceLocation

/**
 * Simple [GraphQLError] implementation that only specified an error message
 */
class MessageGraphQLError(
    private val msg: String?,
) : GraphQLError {
    override fun getMessage(): String? = msg

    override fun getLocations(): List<SourceLocation> = listOf()

    override fun getErrorType(): ErrorClassification? = null
}
