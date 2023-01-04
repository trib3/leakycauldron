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
 * defaults to a [GraphQLContext] built from the passed in [contextMap] and provides access to the
 * [CoroutineScope] contained in that context.
 */
abstract class CoroutineBaseLoader<K, V>(
    private val contextMap: Map<*, Any>,
    coroutineContext: CoroutineContext = Dispatchers.Default,
) :
    KotlinDataLoader<K, V>,
    CoroutineScope by (contextMap[CoroutineScope::class] as? CoroutineScope) ?: CoroutineScope(coroutineContext) {
    /**
     * Return [DataLoaderOptions] to configure the [DataLoader] instance.  By default
     * sets the [org.dataloader.BatchLoaderContextProvider] to be a [GraphQLContext] object
     * with the passed in [contextMap]
     */
    open fun getDataLoaderOptions(): DataLoaderOptions {
        return DataLoaderOptions.newOptions().setBatchLoaderContextProvider {
            GraphQLContext.of(contextMap)
        }
    }
}

/**
 * Convenience class for implementing a [KotlinDataLoader] that provides a [BatchLoaderWithContext]
 * using suspend functions/coroutines to execute the [load].  Will run with a [BatchLoaderEnvironment.context]
 * that is a [GraphQLContext] built from the passed in [contextMap], and execute with
 * the [CoroutineScope] contained in that context.
 */
abstract class CoroutineBatchLoader<K, V>(contextMap: Map<*, Any>) :
    BatchLoaderWithContext<K, V>,
    CoroutineBaseLoader<K, V>(contextMap) {
    /**
     * Suspend function called to batch load the provided [keys] and return a list of loaded values.
     */
    abstract suspend fun loadSuspend(keys: List<K>, environment: BatchLoaderEnvironment): List<V>

    override fun load(keys: List<K>, environment: BatchLoaderEnvironment): CompletionStage<List<V>> {
        return future {
            loadSuspend(keys, environment)
        }
    }

    override fun getDataLoader(): DataLoader<K, V> {
        return DataLoaderFactory.newDataLoader(this::load, getDataLoaderOptions())
    }
}

/**
 * Convenience class for implementing a [KotlinDataLoader] that provides a [MappedBatchLoaderWithContext]
 * using suspend functions/coroutines to execute the [load].  Will run with a [BatchLoaderEnvironment.context]
 * that is a [GraphQLContext] built from the passed in [contextMap], and execute with
 * the [CoroutineScope] contained in that context.
 */
abstract class CoroutineMappedBatchLoader<K, V>(contextMap: Map<*, Any>) :
    MappedBatchLoaderWithContext<K, V>,
    CoroutineBaseLoader<K, V>(contextMap) {
    /**
     * Suspend function called to batch load the provided [keys] and return a map of loaded values.
     */
    abstract suspend fun loadSuspend(keys: Set<K>, environment: BatchLoaderEnvironment): Map<K, V>

    override fun load(keys: Set<K>, environment: BatchLoaderEnvironment): CompletionStage<Map<K, V>> {
        return future {
            loadSuspend(keys, environment)
        }
    }

    override fun getDataLoader(): DataLoader<K, V> {
        return DataLoaderFactory.newMappedDataLoader(this::load, getDataLoaderOptions())
    }
}
