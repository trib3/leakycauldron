package com.trib3.graphql.execution

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.messageContains
import com.expediagroup.graphql.generator.extensions.get
import com.trib3.testing.LeakyMock
import graphql.GraphQLContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.dataloader.BatchLoaderEnvironment
import org.easymock.EasyMock
import org.testng.annotations.Test

class CoroutineBatchLoadersTest {
    @Test
    fun testListLoad() = runBlocking {
        val loader = object : CoroutineBatchLoader<String, String>(mapOf<Any, Any>()) {
            override suspend fun loadSuspend(
                keys: List<String>,
                environment: BatchLoaderEnvironment,
            ): List<String> {
                return keys.map {
                    val charA = 'a'
                    val offset = it.toInt() - 1
                    (charA + offset).toString()
                }
            }

            override val dataLoaderName = "testLoad"
        }
        val mockEnv = LeakyMock.mock<BatchLoaderEnvironment>()
        EasyMock.replay(mockEnv)
        val loaded = loader.load(listOf("1", "2", "3"), mockEnv).await()
        assertThat(loaded).isEqualTo(listOf("a", "b", "c"))
        EasyMock.verify(mockEnv)
    }

    @Test
    fun testMappedLoad() = runBlocking {
        val loader = object : CoroutineMappedBatchLoader<String, String>(mapOf<Any, Any>()) {
            override suspend fun loadSuspend(
                keys: Set<String>,
                environment: BatchLoaderEnvironment,
            ): Map<String, String> {
                return keys.associateBy { it }
            }

            override val dataLoaderName = "testLoad"
        }
        val mockEnv = LeakyMock.mock<BatchLoaderEnvironment>()
        EasyMock.replay(mockEnv)
        val loaded = loader.load(setOf("1", "2", "3"), mockEnv).await()
        assertThat(loaded).isEqualTo(mapOf("1" to "1", "2" to "2", "3" to "3"))
        EasyMock.verify(mockEnv)
    }

    @Test
    fun testCancellation() = runBlocking(Dispatchers.Unconfined) {
        val loader = object : CoroutineMappedBatchLoader<String, String>(mapOf(CoroutineScope::class to this)) {
            override suspend fun loadSuspend(
                keys: Set<String>,
                environment: BatchLoaderEnvironment,
            ): Map<String, String> {
                delay(20000)
                throw IllegalStateException("Should not get here")
            }

            override val dataLoaderName = "testCancel"
        }
        val mockEnv = LeakyMock.mock<BatchLoaderEnvironment>()
        EasyMock.replay(mockEnv)
        val loading = loader.load(setOf("1", "2", "3"), mockEnv)
        this.coroutineContext[Job]?.cancelChildren()
        val startAwaitTime = System.currentTimeMillis()
        assertFailure {
            loading.await()
        }.messageContains("was cancelled")
        // ensure the delay() is not hit, but allow for slow test machines
        assertThat(System.currentTimeMillis() - startAwaitTime).isLessThan(19000)
        EasyMock.verify(mockEnv)
    }

    @Test
    fun testListDataLoaderContext() {
        val batchLoader = object :
            CoroutineBatchLoader<String, String>(mapOf(String::class to "test")) {
            override suspend fun loadSuspend(
                keys: List<String>,
                environment: BatchLoaderEnvironment,
            ): List<String> {
                return keys.map {
                    it + environment.getContext<GraphQLContext>().get<String>() + environment.keyContexts[it]
                }
            }

            override val dataLoaderName = "testListDataLoaderContext"
        }
        val dataLoader = batchLoader.getDataLoader()
        val loadFuture1 = dataLoader.load("1", "a")
        val loadFuture2 = dataLoader.load("2", "b")
        dataLoader.dispatch()
        assertThat(loadFuture1.get()).isEqualTo("1testa")
        assertThat(loadFuture2.get()).isEqualTo("2testb")
    }

    @Test
    fun testMappedDataLoaderContext() {
        val batchLoader = object :
            CoroutineMappedBatchLoader<String, String>(mapOf(String::class to "test")) {
            override suspend fun loadSuspend(
                keys: Set<String>,
                environment: BatchLoaderEnvironment,
            ): Map<String, String> {
                return keys.associateWith {
                    it + environment.getContext<GraphQLContext>().get<String>() + environment.keyContexts[it]
                }
            }

            override val dataLoaderName = "testMappedDataLoaderContext"
        }
        val dataLoader = batchLoader.getDataLoader()
        val loadFuture1 = dataLoader.load("1", "a")
        val loadFuture2 = dataLoader.load("2", "b")
        dataLoader.dispatch()
        assertThat(loadFuture1.get()).isEqualTo("1testa")
        assertThat(loadFuture2.get()).isEqualTo("2testb")
    }
}
