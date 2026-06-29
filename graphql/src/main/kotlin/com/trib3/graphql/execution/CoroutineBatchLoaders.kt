package com.trib3.graphql.execution

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import graphql.GraphQLContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.BatchLoaderWithContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import org.dataloader.MappedBatchLoaderWithContext
import java.util.concurrent.CompletionStage
import kotlin.coroutines.CoroutineContext

/**
 * Base class for implementing a [KotlinDataLoader] that ensures the [BatchLoaderEnvironment.context]
 * defaults to a [GraphQLContext] and provides access to the [CoroutineScope] contained in that context.
 */
abstract class CoroutineBaseLoader<K : Any, V : Any>(
    private val coroutineContext: CoroutineContext = Dispatchers.Default,
) : KotlinDataLoader<K, V> {
    /**
     * Return [DataLoaderOptions] to configure the [DataLoader] instance.  By default
     * sets the [org.dataloader.BatchLoaderContextProvider] to be a [GraphQLContext] object
     * with the passed in [graphQLContext]
     */
    open fun getDataLoaderOptions(graphQLContext: GraphQLContext): DataLoaderOptions =
        DataLoaderOptions
            .newOptions()
            .setBatchLoaderContextProvider {
                graphQLContext
            }.build()

    /**
     * Get a [CoroutineScope] out of the [environment]'s [GraphQLContext] if possible, or construct
     * one given the [coroutineContext]
     */
    fun getScope(environment: BatchLoaderEnvironment): CoroutineScope =
        environment.getContext<GraphQLContext>()?.get(CoroutineScope::class)
            ?: CoroutineScope(coroutineContext)
}

/**
 * Convenience class for implementing a [KotlinDataLoader] that provides a [BatchLoaderWithContext]
 * using suspend functions/coroutines to execute the [load].  Will run with a [BatchLoaderEnvironment.context]
 * that is a [GraphQLContext] and execute with the [CoroutineScope] contained in that context.
 */
abstract class CoroutineBatchLoader<K : Any, V : Any> :
    CoroutineBaseLoader<K, V>(),
    BatchLoaderWithContext<K, V> {
    /**
     * Suspend function called to batch load the provided [keys] and return a list of loaded values.
     */
    abstract suspend fun loadSuspend(
        keys: List<K>,
        environment: BatchLoaderEnvironment,
    ): List<V>

    override fun load(
        keys: List<K>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<List<V>> =
        getScope(environment).future {
            loadSuspend(keys, environment)
        }

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<K, V> =
        DataLoaderFactory.newDataLoader(this::load, getDataLoaderOptions(graphQLContext))
}

/**
 * Convenience class for implementing a [KotlinDataLoader] that provides a [MappedBatchLoaderWithContext]
 * using suspend functions/coroutines to execute the [load].  Will run with a [BatchLoaderEnvironment.context]
 * that is a [GraphQLContext] and execute with the [CoroutineScope] contained in that context.
 */
abstract class CoroutineMappedBatchLoader<K : Any, V : Any> :
    CoroutineBaseLoader<K, V>(),
    MappedBatchLoaderWithContext<K, V> {
    /**
     * Suspend function called to batch load the provided [keys] and return a map of loaded values.
     */
    abstract suspend fun loadSuspend(
        keys: Set<K>,
        environment: BatchLoaderEnvironment,
    ): Map<K, V>

    override fun load(
        keys: Set<K>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<K, V>> =
        getScope(environment).future {
            loadSuspend(keys, environment)
        }

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<K, V> =
        DataLoaderFactory.newMappedDataLoader(this::load, getDataLoaderOptions(graphQLContext))
}
