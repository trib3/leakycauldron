package com.trib3.server.coroutine

import jakarta.inject.Provider
import jakarta.ws.rs.container.ConnectionCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext
import org.glassfish.jersey.server.AsyncContext
import org.glassfish.jersey.server.model.Invocable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * [InvocationHandler] that runs a jersey resource method implemented by a suspend
 * function in a coroutine, and bridges the return/exception value into an [AsyncContext]
 * for the request, allowing suspend functions to transparently implement jersey async requests.
 *
 * Resource classes/methods annotated with [AsyncDispatcher] can specify which standard coroutine dispatcher
 * to launch the coroutine with, otherwise defaults to [Dispatchers.Unconfined].
 * Additionally, if a resource class implements [CoroutineScope], that scope is used to launch the coroutine.
 */
@Suppress("InjectDispatcher") // specify dispatchers by annotation instead of injection
class CoroutineInvocationHandler(
    private val asyncContextProvider: Provider<AsyncContext>,
    private val originalObjectProvider: () -> Any,
    private val originalInvocable: Invocable,
    private val shouldIgnoreReturn: Boolean,
) : InvocationHandler, CoroutineScope by CoroutineScope(Dispatchers.Unconfined) {
    private suspend fun executeCoroutine(
        originalObject: Any,
        args: Array<out Any>?,
        asyncContext: AsyncContext,
    ) {
        try {
            // Can't use .callSuspend() if the object gets subclassed dynamically by AOP,
            // so use suspendCoroutineUninterceptedOrReturn to get the current continuation
            val nonNullArgs = args ?: arrayOf()
            val result: Any? = suspendCoroutineUninterceptedOrReturn { cont ->
                originalInvocable.handlingMethod.invoke(originalObject, *nonNullArgs, cont)
            }
            if (!shouldIgnoreReturn) {
                asyncContext.resume(result)
            }
        } catch (e: Throwable) {
            var cause: Throwable? = e
            while (cause !is CancellationException && cause != null) {
                cause = cause.cause
            }
            if (cause is CancellationException) {
                asyncContext.cancel()
            } else {
                asyncContext.resume(e)
            }
        }
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val asyncContext = asyncContextProvider.get()
        check(asyncContext.isSuspended || asyncContext.suspend()) {
            "Can't suspend!"
        }
        val originalObject = originalObjectProvider.invoke()
        val methodAnnotation = originalInvocable.definitionMethod.getAnnotation(AsyncDispatcher::class.java)
        val classAnnotation =
            originalInvocable.definitionMethod.declaringClass.getAnnotation(AsyncDispatcher::class.java)
        val additionalContext = when ((methodAnnotation ?: classAnnotation)?.dispatcher) {
            "Default" -> Dispatchers.Default
            "IO" -> Dispatchers.IO
            "Main" -> Dispatchers.Main
            "Unconfined" -> Dispatchers.Unconfined
            else -> EmptyCoroutineContext
        }
        val scope = (originalObject as? CoroutineScope) ?: this
        scope.launch(additionalContext + MDCContext()) {
            // cancel this coroutine when a client disconnection is detected by jersey
            asyncContext.register(
                ConnectionCallback {
                    cancel()
                },
            )
            executeCoroutine(originalObject, args, asyncContext)
        }
        return null
    }
}
