package com.trib3.graphql.resources

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasRootCause
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSuccess
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.coxautodev.graphql.tools.GraphQLQueryResolver
import com.expediagroup.graphql.SchemaGeneratorConfig
import com.expediagroup.graphql.TopLevelObject
import com.expediagroup.graphql.execution.GraphQLContext
import com.expediagroup.graphql.toSchema
import com.fasterxml.jackson.module.kotlin.readValue
import com.trib3.config.ConfigLoader
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.ContextScopeKotlinDataFetcherFactoryProvider
import com.trib3.graphql.execution.CustomDataFetcherExceptionHandler
import com.trib3.graphql.execution.GraphQLRequest
import com.trib3.graphql.execution.RequestIdInstrumentation
import com.trib3.graphql.execution.SanitizedGraphQLError
import com.trib3.graphql.websocket.GraphQLContextWebSocketCreatorFactory
import com.trib3.json.ObjectMapperProvider
import com.trib3.server.filters.RequestIdFilter
import com.trib3.testing.LeakyMock
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.execution.AsyncExecutionStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.easymock.EasyMock
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import org.testng.annotations.Test
import java.util.Optional
import java.util.UUID
import kotlin.coroutines.CoroutineContext

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

    suspend fun cancellable(): String {
        delay(100)
        return "result"
    }
}

class GraphQLResourceTest {
    val resource =
        GraphQLResource(
            GraphQL.newGraphQL(
                toSchema(
                    SchemaGeneratorConfig(
                        listOf(this::class.java.packageName),
                        dataFetcherFactoryProvider = ContextScopeKotlinDataFetcherFactoryProvider()
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
    fun testSimpleQuery() = runBlocking {
        val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {test(value:\"123\")}", null, null))
        val graphQLResult = result.entity as ExecutionResult
        assertThat(graphQLResult.getData<Map<String, String>>()["test"]).isEqualTo("123")
    }

    @Test
    fun testVariablesQuery() = runBlocking {
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
    fun testErrorQuery() = runBlocking {
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
    fun testUnknownErrorQuery() = runBlocking {
        val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {unknownError}", mapOf(), null))
        val graphQLResult = result.entity as ExecutionResult
        assertThat(graphQLResult.errors.first()).prop("message", GraphQLError::getMessage)
            .contains("Exception while fetching data")
        val serializedError = objectMapper.writeValueAsString(graphQLResult.errors.first())
        assertThat(objectMapper.readValue<Map<String, *>>(serializedError).keys).doesNotContain("exception")
    }

    @Test
    fun testCancellableQuery() = runBlocking {
        var reached = false
        val requestId = UUID.randomUUID().toString()
        val job = launch {
            RequestIdFilter.withRequestId(requestId) {
                val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {cancellable}", mapOf(), null))
                val entity = result.entity as ExecutionResult
                assertThat(entity.getData<Any>()).isNull()
                assertThat(entity.errors).hasSize(1)
                assertThat(entity.errors[0].message).contains("was cancelled")
                assertThat(entity.extensions["RequestId"]).isEqualTo(requestId)
                reached = true
            }
        }
        while (resource.runningFutures[requestId] == null) {
            delay(1)
        }
        resource.cancel(requestId)
        job.join()
        assertThat(reached).isTrue()
    }

    @Test
    fun testCancellableQueryCompletes() = runBlocking {
        var reached = false
        val requestId = UUID.randomUUID().toString()
        val job = launch {
            RequestIdFilter.withRequestId(requestId) {
                val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {cancellable}", mapOf(), null))
                val entity = result.entity as ExecutionResult
                assertThat(entity.getData<Map<String, String>>()["cancellable"]).isEqualTo("result")
                assertThat(entity.errors).isEmpty()
                assertThat(entity.extensions["RequestId"]).isEqualTo(requestId)
                reached = true
            }
        }
        while (resource.runningFutures[requestId] == null) {
            delay(1)
        }
        resource.cancel("123")
        job.join()
        assertThat(reached).isTrue()
    }

    @Test
    fun testCancellableJoblessContext() {
        val mockScope = LeakyMock.mock<CoroutineScope>()
        val mockContext = LeakyMock.mock<CoroutineContext>()
        EasyMock.expect(mockScope.coroutineContext).andReturn(mockContext).anyTimes()
        EasyMock.expect(mockContext[Job]).andReturn(null).anyTimes()
        EasyMock.replay(mockScope, mockContext)
        resource.runningFutures["987"] = mockScope
        assertThat {
            resource.cancel("987")
        }.isSuccess()
    }
}
