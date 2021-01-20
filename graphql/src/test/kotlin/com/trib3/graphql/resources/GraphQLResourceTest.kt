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
import com.trib3.server.config.TribeApplicationConfig
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
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import org.testng.annotations.Test
import java.util.Optional
import java.util.UUID
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.ClientErrorException
import javax.ws.rs.container.ContainerRequestContext
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

    fun unauthorizedError(): String {
        throw ClientErrorException(HttpStatus.UNAUTHORIZED_401)
    }

    suspend fun cancellable(): String {
        delay(100)
        return "result"
    }
}

class GraphQLResourceTest {
    val graphQL = GraphQL.newGraphQL(
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
        .build()
    val wsCreatorFactory = object : GraphQLContextWebSocketCreatorFactory {
        override fun getCreator(containerRequestContext: ContainerRequestContext): WebSocketCreator {
            return WebSocketCreator { _, _ -> null }
        }
    }
    val resource =
        GraphQLResource(
            graphQL,
            GraphQLConfig(ConfigLoader("GraphQLResourceTest")),
            wsCreatorFactory,
            appConfig = TribeApplicationConfig(ConfigLoader())
        )
    val lockedResource =
        GraphQLResource(
            graphQL,
            GraphQLConfig(ConfigLoader("GraphQLResourceIntegrationTest")),
            wsCreatorFactory,
            appConfig = TribeApplicationConfig(ConfigLoader())
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
    fun testUpgradeNoContainer() {
        val mockReq = LeakyMock.niceMock<HttpServletRequest>()
        val mockRes = LeakyMock.niceMock<HttpServletResponse>()
        val mockCtx = LeakyMock.niceMock<ContainerRequestContext>()
        EasyMock.expect(mockReq.getHeader("Origin")).andReturn("http://localhost")
        EasyMock.expect(mockRes.getHeader(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER))
            .andReturn("http://localhost")
        EasyMock.replay(mockReq, mockRes, mockCtx)
        val resp = resource.graphQLUpgrade(Optional.empty(), mockReq, mockRes, mockCtx)
        assertThat(resp.status).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED_405)
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
    fun testUnauthorizedErrorQuery() = runBlocking {
        val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {unauthorizedError}", mapOf(), null))
        assertThat(result.status).isEqualTo(HttpStatus.UNAUTHORIZED_401)
        assertThat(result.entity).isNull()
        assertThat(result.getHeaderString("WWW-Authenticate")).isEqualTo("Basic realm=\"realm\"")
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
        resource.cancel(Optional.empty(), requestId)
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
        resource.cancel(Optional.empty(), "123")
        job.join()
        assertThat(reached).isTrue()
    }

    @Test
    fun testCancellableQueryNeedsAuthToCancel() = runBlocking {
        var reached = false
        val requestId = UUID.randomUUID().toString()
        val job = launch {
            RequestIdFilter.withRequestId(requestId) {
                val result =
                    lockedResource.graphQL(
                        Optional.of(UserPrincipal(User("bill"))),
                        GraphQLRequest("query {cancellable}", mapOf(), null)
                    )
                val entity = result.entity as ExecutionResult
                assertThat(entity.getData<Map<String, String>>()["cancellable"]).isEqualTo("result")
                assertThat(entity.errors).isEmpty()
                assertThat(entity.extensions["RequestId"]).isEqualTo(requestId)
                reached = true
            }
        }
        while (lockedResource.runningFutures[requestId] == null) {
            delay(1)
        }
        assertThat(
            lockedResource.cancel(
                Optional.of(UserPrincipal(User("bill"))),
                "123"
            ).status
        ).isEqualTo(HttpStatus.NO_CONTENT_204)
        job.join()
        assertThat(reached).isTrue()
    }

    @Test
    fun testCancellableQueryWithoutAuth() = runBlocking {
        var reached = false
        val requestId = UUID.randomUUID().toString()
        val job = launch {
            RequestIdFilter.withRequestId(requestId) {
                val result =
                    lockedResource.graphQL(
                        Optional.of(UserPrincipal(User("bill"))),
                        GraphQLRequest("query {cancellable}", mapOf(), null)
                    )
                val entity = result.entity as ExecutionResult
                assertThat(entity.getData<Map<String, String>>()["cancellable"]).isEqualTo("result")
                assertThat(entity.errors).isEmpty()
                assertThat(entity.extensions["RequestId"]).isEqualTo(requestId)
                reached = true
            }
        }
        while (lockedResource.runningFutures[requestId] == null) {
            delay(1)
        }
        assertThat(lockedResource.cancel(Optional.empty(), "123").status).isEqualTo(HttpStatus.UNAUTHORIZED_401)
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
            resource.cancel(Optional.empty(), "987")
        }.isSuccess()
    }
}
