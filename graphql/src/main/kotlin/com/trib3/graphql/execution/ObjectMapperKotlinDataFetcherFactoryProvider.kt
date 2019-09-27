package com.trib3.graphql.execution

import com.expedia.graphql.execution.FunctionDataFetcher
import com.expedia.graphql.execution.KotlinDataFetcherFactoryProvider
import com.expedia.graphql.hooks.SchemaGeneratorHooks
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.schema.DataFetcherFactories
import graphql.schema.DataFetcherFactory
import kotlin.reflect.KFunction

/**
 * Extends the [KotlinDataFetcherFactoryProvider] to allow specifying
 * the [ObjectMapper] used for json reading/writing
 */
class ObjectMapperKotlinDataFetcherFactoryProvider(
    private val mapper: ObjectMapper,
    private val hooks: SchemaGeneratorHooks
) :
    KotlinDataFetcherFactoryProvider(hooks) {
    override fun functionDataFetcherFactory(
        target: Any?,
        kFunction: KFunction<*>
    ): DataFetcherFactory<Any> {
        return DataFetcherFactories.useDataFetcher(
            FunctionDataFetcher(
                target = target,
                fn = kFunction,
                objectMapper = mapper,
                executionPredicate = hooks.dataFetcherExecutionPredicate
            )
        )
    }
}
