package com.trib3.server.graphql

import com.trib3.server.filters.RequestIdFilter
import graphql.ExecutionResult
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reactive subscriber for consuming published graphql streaming events and sending them to the socket
 *
 * @param socket the websocket to communicate data to
 * @param messageId the message id that started the query
 * @param loggingRequestId requestId value for logging
 */
class GraphQLQuerySubscriber(val socket: GraphQLWebSocket, val messageId: String?, val loggingRequestId: String) :
    Subscriber<ExecutionResult> {

    private lateinit var subscription: Subscription
    private val resultCount = AtomicInteger(0)

    override fun onComplete() {
        RequestIdFilter.withRequestId(loggingRequestId) {
            socket.onQueryFinished(this, resultCount.get())
        }
    }

    override fun onSubscribe(subscription: Subscription) {
        RequestIdFilter.withRequestId(loggingRequestId) {
            this.subscription = subscription
            subscription.request(1)
        }
    }

    override fun onNext(result: ExecutionResult) {
        RequestIdFilter.withRequestId(loggingRequestId) {
            val instrumentedResult = RequestIdInstrumentation()
                .instrumentExecutionResult(result, null).get()
            socket.sendMessage(OperationType.GQL_DATA, messageId, instrumentedResult)
            resultCount.incrementAndGet()
            subscription.request(1)
        }
    }

    override fun onError(cause: Throwable) {
        RequestIdFilter.withRequestId(loggingRequestId) {
            socket.onQueryFinished(this, resultCount.get(), cause)
        }
    }

    fun unsubscribe() {
        subscription.cancel()
    }
}
