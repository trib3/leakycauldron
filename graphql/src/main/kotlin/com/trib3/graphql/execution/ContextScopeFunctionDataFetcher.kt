package com.trib3.graphql.execution

import com.expediagroup.graphql.generator.execution.FunctionDataFetcher
import com.expediagroup.graphql.generator.execution.SimpleKotlinDataFetcherFactoryProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.trib3.graphql.resources.getInstance
import graphql.schema.DataFetcherFactory
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.instanceParameter

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
 * if the context is a [CoroutineScope].  Otherwise runs in a scope with [Dispatchers.Default].
 */
open class ContextScopeFunctionDataFetcher(
    private val target: Any?,
    private val fn: KFunction<*>,
    objectMapper: ObjectMapper = jacksonObjectMapper()
) : FunctionDataFetcher(target, fn, objectMapper), CoroutineScope by CoroutineScope(Dispatchers.Default) {
    override fun get(environment: DataFetchingEnvironment): Any? {
        val instance: Any? = target ?: environment.getSource<Any?>()
        val instanceParameter = fn.instanceParameter

        return if (instance != null && instanceParameter != null) {
            val parameterValues = getParameters(fn, environment)
                .plus(instanceParameter to instance)

            if (fn.isSuspend) {
                val scope = environment.graphQlContext.getInstance<CoroutineScope>() ?: this
                runScopedSuspendingFunction(parameterValues, scope)
            } else {
                runBlockingFunction(parameterValues)
            }
        } else {
            null
        }
    }

    protected open fun runScopedSuspendingFunction(
        parameterValues: Map<KParameter, Any?>,
        scope: CoroutineScope,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        coroutineStart: CoroutineStart = CoroutineStart.DEFAULT
    ): CompletableFuture<Any?> {
        return scope.future(coroutineContext, coroutineStart) {
            try {
                fn.callSuspendBy(parameterValues)
            } catch (exception: InvocationTargetException) {
                throw exception.cause ?: exception
            }
        }
    }
}
