package com.trib3.graphql.execution

import com.fasterxml.jackson.annotation.JsonIgnore
import graphql.ExceptionWhileDataFetching
import graphql.execution.ExecutionPath
import graphql.language.SourceLocation

/**
 * Removes exception from the error JSON serialization keeping it out of the API response
 * and attempts to bubble up the message from the root cause of the exception
 */
class SanitizedGraphQLError(
    path: ExecutionPath,
    exception: Throwable,
    sourceLocation: SourceLocation
) : ExceptionWhileDataFetching(path, exception, sourceLocation) {

    override fun getMessage(): String {
        return getCause(super.getException()).message ?: super.getMessage()
    }

    @JsonIgnore
    override fun getException(): Throwable {
        return super.getException()
    }

    /**
     * Find the root cause of the exception being thrown
     * (Root cause will either have a null or itself under it)
     */
    private fun getCause(e: Throwable): Throwable {
        var result: Throwable = e

        while (result.cause != null && result != result.cause) {
            result = result.cause!!
        }
        return result
    }
}
