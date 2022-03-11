package com.trib3.server.coroutine

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
import javax.inject.Provider
import javax.ws.rs.container.ConnectionCallback
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
class CoroutineInvocationHandler(
    private val asyncContextProvider: Provider<AsyncContext>,
    private val originalObjectProvider: () -> Any,
    private val originalInvocable: Invocable
) : InvocationHandler, CoroutineScope by CoroutineScope(Dispatchers.Unconfined) {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val asyncContext = asyncContextProvider.get()
        if (!asyncContext.suspend()) {
            throw IllegalStateException("Can't suspend!")
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
                }
            )
            try {
                // Can't use .callSuspend() if the object gets subclassed dynamically by AOP,
                // so use suspendCoroutineUninterceptedOrReturn to get the current continuation
                val nonNullArgs = args ?: arrayOf()
                val result: Any? = suspendCoroutineUninterceptedOrReturn { cont ->
                    originalInvocable.handlingMethod.invoke(originalObject, *nonNullArgs, cont)
                }
                asyncContext.resume(result)
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
        return null
    }
}
