package com.trib3.graphql.execution

import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import mu.KotlinLogging
import java.util.concurrent.CompletableFuture

private val log = KotlinLogging.logger { }

/**
 * Custom Exception Handler implantation allowing control over sanitation, relevance and specificity
 */
class CustomDataFetcherExceptionHandler : DataFetcherExceptionHandler {
    override fun handleException(
        handlerParameters: DataFetcherExceptionHandlerParameters,
    ): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val exception = handlerParameters.exception
        val sourceLocation = handlerParameters.sourceLocation
        val path = handlerParameters.path
        val error: GraphQLError = SanitizedGraphQLError(path, exception, sourceLocation)
        log.error("Error in data fetching: ${error.message}", exception)
        return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult().error(error).build())
    }
}
