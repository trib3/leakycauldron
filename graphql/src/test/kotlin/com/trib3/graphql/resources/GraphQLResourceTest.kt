package com.trib3.graphql.resources

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasRootCause
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import com.coxautodev.graphql.tools.GraphQLQueryResolver
import com.expediagroup.graphql.SchemaGeneratorConfig
import com.expediagroup.graphql.TopLevelObject
import com.expediagroup.graphql.execution.GraphQLContext
import com.expediagroup.graphql.toSchema
import com.fasterxml.jackson.module.kotlin.readValue
import com.trib3.config.ConfigLoader
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.CustomDataFetcherExceptionHandler
import com.trib3.graphql.execution.GraphQLRequest
import com.trib3.graphql.execution.RequestIdInstrumentation
import com.trib3.graphql.execution.SanitizedGraphQLError
import com.trib3.graphql.websocket.GraphQLContextWebSocketCreatorFactory
import com.trib3.json.ObjectMapperProvider
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.execution.AsyncExecutionStrategy
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import org.testng.annotations.Test
import java.util.Optional

class TestQuery : GraphQLQueryResolver {
    fun test(value: String): String {
        return value
    }

    fun error(): String {
        throw IllegalArgumentException("an error was thrown")
    }

    fun unknownError(): String {
        throw IllegalArgumentException()
    }
}

class GraphQLResourceTest {
    val resource =
        GraphQLResource(
            GraphQL.newGraphQL(
                toSchema(
                    SchemaGeneratorConfig(
                        listOf(this::class.java.packageName)
                    ),
                    listOf(TopLevelObject(TestQuery())),
                    listOf(),
                    listOf()
                )
            )
                .queryExecutionStrategy(AsyncExecutionStrategy(CustomDataFetcherExceptionHandler()))
                .instrumentation(RequestIdInstrumentation())
                .build(),
            GraphQLConfig(ConfigLoader("GraphQLResourceTest")),
            object : GraphQLContextWebSocketCreatorFactory {
                override fun getCreator(context: GraphQLContext): WebSocketCreator {
                    return WebSocketCreator { _, _ -> null }
                }
            }
        )
    val objectMapper = ObjectMapperProvider().get()

    @Test
    fun testPolicy() {
        assertThat(resource.webSocketFactory.policy.idleTimeout).isEqualTo(200000)
        assertThat(resource.webSocketFactory.policy.maxBinaryMessageSize).isEqualTo(300000)
        assertThat(resource.webSocketFactory.policy.maxTextMessageSize).isEqualTo(400000)
    }

    @Test
    fun testSimpleQuery() {
        val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {test(value:\"123\")}", null, null))
        val graphQLResult = result.entity as ExecutionResult
        assertThat(graphQLResult.getData<Map<String, String>>()["test"]).isEqualTo("123")
    }

    @Test
    fun testVariablesQuery() {
        val result = resource.graphQL(
            Optional.empty(),
            GraphQLRequest(
                "query(${'$'}val:String!) {test(value:${'$'}val)}",
                mapOf("val" to "123"),
                null
            )
        )
        val graphQLResult = result.entity as ExecutionResult
        assertThat(graphQLResult.getData<Map<String, String>>()["test"]).isEqualTo("123")
    }

    @Test
    fun testErrorQuery() {
        val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {error}", mapOf(), null))
        val graphQLResult = result.entity as ExecutionResult
        assertThat(graphQLResult.errors.first()).prop("message", GraphQLError::getMessage)
            .isEqualTo("an error was thrown")
        assertThat(graphQLResult.errors.first()).isInstanceOf(SanitizedGraphQLError::class)
        assertThat((graphQLResult.errors.first() as SanitizedGraphQLError).exception)
            .hasRootCause(IllegalArgumentException("an error was thrown"))
        val serializedError = objectMapper.writeValueAsString(graphQLResult.errors.first())
        assertThat(objectMapper.readValue<Map<String, *>>(serializedError).keys).doesNotContain("exception")
    }

    @Test
    fun testUnknownErrorQuery() {
        val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {unknownError}", mapOf(), null))
        val graphQLResult = result.entity as ExecutionResult
        assertThat(graphQLResult.errors.first()).prop("message", GraphQLError::getMessage)
            .contains("Exception while fetching data")
        val serializedError = objectMapper.writeValueAsString(graphQLResult.errors.first())
        assertThat(objectMapper.readValue<Map<String, *>>(serializedError).keys).doesNotContain("exception")
    }
}
