package com.trib3.graphql.execution

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import graphql.GraphQLContext
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationState
import org.dataloader.DataLoaderRegistry
import org.testng.annotations.Test

class GraphQLRequestTest {
    @Test
    fun testExtensionMethod() {
        val request = GraphQLRequest("query test", mapOf(), "test")
        val executionInput = request.toExecutionInput()
        assertThat(executionInput.query).isEqualTo(request.query)
        assertThat(executionInput.dataLoaderRegistry).isEqualTo(
            DataLoaderDispatcherInstrumentationState.EMPTY_DATALOADER_REGISTRY
        )
        assertThat(executionInput.context).isInstanceOf(GraphQLContext::class)

        val emptyRegistry = DataLoaderRegistry()
        val executionInputWithArgs = request.toExecutionInput("context object") { _, _ ->
            emptyRegistry
        }
        assertThat(executionInputWithArgs.query).isEqualTo(request.query)
        assertThat(executionInputWithArgs.dataLoaderRegistry).isEqualTo(emptyRegistry)
        assertThat(executionInputWithArgs.context).isInstanceOf(String::class).isEqualTo("context object")
    }
}
