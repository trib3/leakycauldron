package com.trib3.graphql.resources

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isNullOrEmpty
import assertk.assertions.isSuccess
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.operations.Query
import com.expediagroup.graphql.server.types.GraphQLBatchRequest
import com.expediagroup.graphql.server.types.GraphQLBatchResponse
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLResponse
import com.expediagroup.graphql.server.types.GraphQLServerError
import com.fasterxml.jackson.module.kotlin.readValue
import com.trib3.config.ConfigLoader
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.CustomDataFetcherExceptionHandler
import com.trib3.graphql.execution.RequestIdInstrumentation
import com.trib3.json.ObjectMapperProvider
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.filters.RequestIdFilter
import com.trib3.testing.LeakyMock
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.easymock.EasyMock
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.testng.annotations.Test
import java.util.Optional
import java.util.UUID
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.ClientErrorException
import javax.ws.rs.container.ContainerRequestContext
import kotlin.coroutines.CoroutineContext

class TestQuery : Query {
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
            ),
            listOf(TopLevelObject(TestQuery())),
            listOf(),
            listOf(),
        ),
    )
        .queryExecutionStrategy(AsyncExecutionStrategy(CustomDataFetcherExceptionHandler()))
        .instrumentation(RequestIdInstrumentation())
        .build()

    val resource =
        GraphQLResource(
            graphQL,
            GraphQLConfig(ConfigLoader("GraphQLResourceTest")),
            appConfig = TribeApplicationConfig(ConfigLoader()),
            creator = { _, _ -> null },
        )
    val lockedResource =
        GraphQLResource(
            graphQL,
            GraphQLConfig(ConfigLoader("GraphQLResourceIntegrationTest")),
            appConfig = TribeApplicationConfig(ConfigLoader()),
            creator = { _, _ -> null },
        )
    val objectMapper = ObjectMapperProvider().get()

    @Test
    fun testSimpleQuery() = runBlocking {
        RequestIdFilter.withRequestId("test-simple-query") {
            val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {test(value:\"123\")}"))
            val graphQLResult = result.entity as GraphQLResponse<*>
            assertThat((graphQLResult.data as Map<*, *>)["test"]).isEqualTo("123")
            assertThat(resource.runningFutures["test-simple-query"]).isNull()
        }
    }

    @Test
    fun testBatchQuery() = runBlocking {
        RequestIdFilter.withRequestId("test-batch-query") {
            val result = resource.graphQL(
                Optional.empty(),
                GraphQLBatchRequest(
                    GraphQLRequest("query {test(value:\"123\")}"),
                    GraphQLRequest("query {test(value:\"456\")}"),
                ),
            )
            val graphQLResult = result.entity as GraphQLBatchResponse
            assertThat(graphQLResult.responses).hasSize(2)
            assertThat((graphQLResult.responses[0].data as Map<*, *>)["test"]).isEqualTo("123")
            assertThat((graphQLResult.responses[1].data as Map<*, *>)["test"]).isEqualTo("456")
            assertThat(resource.runningFutures["test-batch-query"]).isNull()
        }
    }

    @Test
    fun testUpgradeNoContainer() {
        val mockReq = LeakyMock.niceMock<HttpServletRequest>()
        val mockRes = LeakyMock.niceMock<HttpServletResponse>()
        val mockCtx = LeakyMock.niceMock<ContainerRequestContext>()
        EasyMock.expect(mockReq.pathInfo).andReturn("/graphql")
        EasyMock.expect(mockReq.getHeader("Origin")).andReturn("http://test1.leakycauldron.trib3.com")
        EasyMock.expect(mockRes.getHeader(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER))
            .andReturn("http://test1.leakycauldron.trib3.com")
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
                null,
                mapOf("val" to "123"),
            ),
        )
        val graphQLResult = result.entity as GraphQLResponse<*>
        assertThat((graphQLResult.data as Map<*, *>)["test"]).isEqualTo("123")
    }

    @Test
    fun testErrorQuery() = runBlocking {
        val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {error}"))
        val graphQLResult = result.entity as GraphQLResponse<*>
        val errors = graphQLResult.errors
        assertThat(errors?.first()).isNotNull().prop("message", GraphQLServerError::message)
            .isEqualTo("an error was thrown")
        val serializedError = objectMapper.writeValueAsString(graphQLResult.errors?.first())
        assertThat(objectMapper.readValue<Map<String, *>>(serializedError).keys).doesNotContain("exception")
    }

    @Test
    fun testUnauthorizedErrorQuery() = runBlocking {
        val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {unauthorizedError}"))
        assertThat(result.status).isEqualTo(HttpStatus.UNAUTHORIZED_401)
        assertThat(result.entity).isNull()
        assertThat(result.getHeaderString("WWW-Authenticate")).isEqualTo("Basic realm=\"realm\"")
    }

    @Test
    fun testUnknownErrorQuery() = runBlocking {
        val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {unknownError}"))
        val graphQLResult = result.entity as GraphQLResponse<*>
        assertThat(graphQLResult.errors?.first()).isNotNull().prop("message", GraphQLServerError::message)
            .contains("Exception while fetching data")
        val serializedError = objectMapper.writeValueAsString(graphQLResult.errors?.first())
        assertThat(objectMapper.readValue<Map<String, *>>(serializedError).keys).doesNotContain("exception")
    }

    @Test
    fun testCancellableQuery() = runBlocking {
        var reached = false
        val requestId = UUID.randomUUID().toString()
        val job = launch {
            RequestIdFilter.withRequestId(requestId) {
                val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {cancellable}"))
                val entity = result.entity as GraphQLResponse<*>
                assertThat(entity.data).isNull()
                assertThat(entity.errors).isNotNull().hasSize(1)
                assertThat(entity.errors!![0].message).contains("was cancelled")
                assertThat(entity.extensions!!["RequestId"]).isEqualTo(requestId)
                assertThat(resource.runningFutures[requestId]).isNull()
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
                val result = resource.graphQL(Optional.empty(), GraphQLRequest("query {cancellable}"))
                val entity = result.entity as GraphQLResponse<*>
                assertThat((entity.data as Map<*, *>)["cancellable"]).isEqualTo("result")
                assertThat(entity.errors).isNullOrEmpty()
                assertThat(entity.extensions!!["RequestId"]).isEqualTo(requestId)
                assertThat(resource.runningFutures[requestId]).isNull()
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
                        GraphQLRequest("query {cancellable}"),
                    )
                val entity = result.entity as GraphQLResponse<*>
                assertThat((entity.data as Map<*, *>)["cancellable"]).isEqualTo("result")
                assertThat(entity.errors).isNullOrEmpty()
                assertThat(entity.extensions!!["RequestId"]).isEqualTo(requestId)
                assertThat(resource.runningFutures[requestId]).isNull()
                reached = true
            }
        }
        while (lockedResource.runningFutures[requestId] == null) {
            delay(1)
        }
        assertThat(
            lockedResource.cancel(
                Optional.of(UserPrincipal(User("bill"))),
                "123",
            ).status,
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
                        GraphQLRequest("query {cancellable}"),
                    )
                val entity = result.entity as GraphQLResponse<*>
                assertThat((entity.data as Map<*, *>)["cancellable"]).isEqualTo("result")
                assertThat(entity.errors).isNullOrEmpty()
                assertThat(entity.extensions!!["RequestId"]).isEqualTo(requestId)
                assertThat(resource.runningFutures[requestId]).isNull()
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
