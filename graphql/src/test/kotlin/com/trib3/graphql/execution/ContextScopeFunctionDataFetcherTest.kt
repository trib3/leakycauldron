package com.trib3.graphql.execution

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.fail
import com.trib3.graphql.resources.getGraphQLContextMap
import com.trib3.testing.LeakyMock
import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.easymock.EasyMock
import org.testng.annotations.Test
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture

class ContextScopeQuery {
    suspend fun coroutine(): String {
        delay(100)
        return "coroutine"
    }

    suspend fun coroutineException(): String {
        delay(1)
        throw InvocationTargetException(IllegalStateException("boom"))
    }

    suspend fun coroutineExceptionNoCause(): String {
        delay(1)
        throw InvocationTargetException(null, "boom")
    }
}

class ContextScopeFunctionDataFetcherTest {

    @Test
    fun testNoTarget() {
        val fetcher = ContextScopeFunctionDataFetcher(null, ContextScopeQuery::coroutine)
        val mockEnv = LeakyMock.mock<DataFetchingEnvironment>()
        EasyMock.expect(mockEnv.graphQlContext).andReturn(GraphQLContext.newContext().build())
        EasyMock.expect(mockEnv.getSource<Any?>()).andReturn(null)
        EasyMock.replay(mockEnv)
        assertThat(fetcher.get(mockEnv)).isNull()
    }

    @Test
    fun testNoScopeSuccess() = runBlocking {
        val fetcher = ContextScopeFunctionDataFetcher(ContextScopeQuery(), ContextScopeQuery::coroutine)
        val mockEnv = LeakyMock.mock<DataFetchingEnvironment>()
        EasyMock.expect(mockEnv.graphQlContext).andReturn(GraphQLContext.newContext().build())
        EasyMock.replay(mockEnv)
        val future = fetcher.get(mockEnv) as CompletableFuture<*>
        assertThat(future.await()).isEqualTo("coroutine")
    }

    @Test
    fun testNoScopeError() = runBlocking {
        val fetcher = ContextScopeFunctionDataFetcher(ContextScopeQuery(), ContextScopeQuery::coroutineExceptionNoCause)
        val mockEnv = LeakyMock.mock<DataFetchingEnvironment>()
        EasyMock.expect(mockEnv.graphQlContext).andReturn(GraphQLContext.newContext().build())
        EasyMock.replay(mockEnv)
        val future = fetcher.get(mockEnv) as CompletableFuture<*>
        try {
            future.await()
            fail("Should not get here")
        } catch (e: Exception) {
            assertThat(e.message).isEqualTo("boom")
        }
    }

    @Test
    fun testNoScopeCancel() = runBlocking {
        val fetcher = ContextScopeFunctionDataFetcher(ContextScopeQuery(), ContextScopeQuery::coroutine)
        val mockEnv = LeakyMock.mock<DataFetchingEnvironment>()
        EasyMock.expect(mockEnv.graphQlContext).andReturn(GraphQLContext.newContext().build())
        EasyMock.replay(mockEnv)
        val scope = this
        val future = fetcher.get(mockEnv) as CompletableFuture<*>
        launch {
            scope.coroutineContext[Job]?.cancelChildren()
        }
        assertThat(future.await()).isEqualTo("coroutine")
    }

    @Test
    fun testScopeSuccess() = runBlocking {
        val scope = this
        val fetcher = ContextScopeFunctionDataFetcher(ContextScopeQuery(), ContextScopeQuery::coroutine)
        val mockEnv = LeakyMock.mock<DataFetchingEnvironment>()
        EasyMock.expect(mockEnv.graphQlContext).andReturn(GraphQLContext.of(getGraphQLContextMap(scope)))
        EasyMock.replay(mockEnv)
        val future = fetcher.get(mockEnv) as CompletableFuture<*>
        assertThat(future.await()).isEqualTo("coroutine")
    }

    @Test
    fun testScopeError() = runBlocking {
        supervisorScope {
            val scope = this
            val fetcher = ContextScopeFunctionDataFetcher(ContextScopeQuery(), ContextScopeQuery::coroutineException)
            val mockEnv = LeakyMock.mock<DataFetchingEnvironment>()
            EasyMock.expect(mockEnv.graphQlContext).andReturn(GraphQLContext.of(getGraphQLContextMap(scope)))
            EasyMock.replay(mockEnv)
            val future = fetcher.get(mockEnv) as CompletableFuture<*>
            try {
                future.await()
                fail("Should not get here")
            } catch (e: Exception) {
                assertThat(e.message).isEqualTo("boom")
            }
        }
    }

    @Test
    fun testScopeCancel() = runBlocking<Unit> {
        supervisorScope {
            val scope = this
            val fetcher = ContextScopeFunctionDataFetcher(ContextScopeQuery(), ContextScopeQuery::coroutine)
            val mockEnv = LeakyMock.mock<DataFetchingEnvironment>()
            EasyMock.expect(mockEnv.graphQlContext).andReturn(GraphQLContext.of(getGraphQLContextMap(scope)))
            EasyMock.replay(mockEnv)
            val future = fetcher.get(mockEnv) as CompletableFuture<*>
            launch {
                scope.coroutineContext[Job]?.cancelChildren()
            }
            try {
                future.await()
                fail("Should not get here")
            } catch (e: Exception) {
                assertThat(e.message).isNotNull().contains("was cancelled")
            }
        }
    }
}
