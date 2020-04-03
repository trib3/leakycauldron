package com.trib3.graphql.execution

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import graphql.ExecutionResult
import graphql.GraphQLError

/**
 * Jackson mixin for [ExecutionResult]s that ensures keys for data/errors/extensions
 * only get written if there is corresponding data.  Similar to [ExecutionResult.toSpecification],
 * but keeps the object typed as an [ExecutionResult] instead of converting to a [Map]
 */
interface JsonSafeExecutionResultMixin : ExecutionResult {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    override fun getErrors(): List<GraphQLError>

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    override fun getExtensions(): Map<Any, Any>

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    override fun <T> getData(): T

    @JsonIgnore
    override fun isDataPresent(): Boolean

    @JsonIgnore
    override fun toSpecification(): Map<String, Any>
}
