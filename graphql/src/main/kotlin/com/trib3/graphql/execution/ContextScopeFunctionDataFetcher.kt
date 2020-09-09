package com.trib3.graphql.execution

import com.expediagroup.graphql.execution.FunctionDataFetcher
import com.expediagroup.graphql.execution.SimpleKotlinDataFetcherFactoryProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import graphql.schema.DataFetcherFactory
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend

/**
 * [SimpleKotlinDataFetcherFactoryProvider] subclass that provides a
 * [ContextScopeFunctionDataFetcher] to allow for structured concurrency
 * based on the scope in the GraphQL context.
 */
open class ContextScopeKotlinDataFetcherFactoryProvider(
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) : SimpleKotlinDataFetcherFactoryProvider(objectMapper) {

    override fun functionDataFetcherFactory(target: Any?, kFunction: KFunction<*>) = DataFetcherFactory {
        ContextScopeFunctionDataFetcher(
            target = target,
            fn = kFunction,
            objectMapper = objectMapper
        )
    }
}

/**
 * [FunctionDataFetcher] that tries to run suspend functions
 * in a [CoroutineScope] provided by the [DataFetchingEnvironment.getContext],
 * if the context is a [CoroutineScope].  Otherwise runs in [GlobalScope] like
 * [FunctionDataFetcher].
 */
open class ContextScopeFunctionDataFetcher(
    private val target: Any?,
    private val fn: KFunction<*>,
    objectMapper: ObjectMapper = jacksonObjectMapper()
) : FunctionDataFetcher(target, fn, objectMapper) {
    override fun get(environment: DataFetchingEnvironment): Any? {
        val instance: Any? = target ?: environment.getSource()

        return instance?.let {
            val parameterValues = getParameterValues(fn, environment)

            if (fn.isSuspend) {
                val scope = (environment.getContext<Any?>() as? CoroutineScope) ?: GlobalScope
                runScopedSuspendingFunction(it, parameterValues, scope)
            } else {
                runBlockingFunction(it, parameterValues)
            }
        }
    }

    protected open fun runScopedSuspendingFunction(
        instance: Any,
        parameterValues: Array<Any?>,
        scope: CoroutineScope,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        coroutineStart: CoroutineStart = CoroutineStart.DEFAULT
    ): CompletableFuture<Any?> {
        return scope.future(coroutineContext, coroutineStart) {
            try {
                fn.callSuspend(instance, *parameterValues)
            } catch (exception: InvocationTargetException) {
                throw exception.cause ?: exception
            }
        }
    }
}
