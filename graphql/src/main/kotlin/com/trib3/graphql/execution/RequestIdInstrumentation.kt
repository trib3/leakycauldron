package com.trib3.graphql.execution

import com.trib3.server.filters.RequestIdFilter
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import org.slf4j.MDC
import java.util.concurrent.CompletableFuture

/**
 * Instruments a GraphQL response's extensions with the RequestId stored in the logging [MDC] by
 * the [RequestIdFilter]
 */
class RequestIdInstrumentation : SimplePerformantInstrumentation() {
    override fun instrumentExecutionResult(
        executionResult: ExecutionResult,
        parameters: InstrumentationExecutionParameters,
        state: InstrumentationState?,
    ): CompletableFuture<ExecutionResult> {
        return CompletableFuture.completedFuture(
            ExecutionResultImpl.newExecutionResult()
                .from(executionResult)
                .addExtension(RequestIdFilter.REQUEST_ID_KEY, RequestIdFilter.getRequestId())
                .build(),
        )
    }
}
