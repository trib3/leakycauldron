package com.trib3.server.graphql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.trib3.server.filters.RequestIdFilter
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import mu.KotlinLogging
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.reactivestreams.Publisher
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val log = KotlinLogging.logger { }

/**
 * Class that stores the success and failure callbacks for sending keepalive messages to the client
 */
internal class KeepAliveCallbacks(private val socket: GraphQLWebSocket, private val messageId: String?) {
    fun onInterval(count: Long) {
        log.trace("Sent $count keepalive messages")
        socket.sendMessage(OperationType.GQL_CONNECTION_KEEP_ALIVE, messageId)
    }

    fun onError(e: Throwable) {
        log.error("Error in keepalive subscriber ${e.message}", e)
    }
}

/**
 * A [WebSocketAdapter] that accepts a GraphQL query and executes it.  Returns a GraphQL ExecutionResult
 * for regular operations, but if a subscription operation returns a [Publisher], will stream an
 * ExecutionResult over the websocket for each published result.
 *
 * Implements https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */
open class GraphQLWebSocket(
    val graphQL: GraphQL?,
    val objectMapper: ObjectMapper,
    val keepAliveIntervalSeconds: Long = 15,
    val scheduler: Scheduler = Schedulers.io() // use the io scheduler by default
) : WebSocketAdapter() {
    val objectWriter = objectMapper.writerWithDefaultPrettyPrinter()!!

    private val remoteLock = ReentrantLock()

    private val runningQueryLock = ReentrantLock()
    @Volatile
    internal var runningQuerySubscriber: GraphQLQuerySubscriber? = null
        private set

    private val keepAliveLock = ReentrantLock()
    @Volatile
    internal var keepAliveInterval: Disposable? = null
        private set

    /**
     * Launch a new query if one is not already running
     */
    private fun handleQueryStart(unparsed: String) {
        check(graphQL != null) {
            "graphQL not configured!"
        }
        runningQueryLock.withLock {
            check(runningQuerySubscriber == null) {
                "Query ${runningQuerySubscriber?.loggingRequestId} is already running!"
            }
            val message = objectMapper.readValue<OperationMessage<GraphRequest>>(unparsed)
            check(message.id != null) {
                "Must pass a message id to start a query"
            }
            val payload = message.payload!!
            val loggingRequestId = RequestIdFilter.getRequestId()!!
            val subscriber = GraphQLQuerySubscriber(this, message.id, loggingRequestId)
            this.runningQuerySubscriber = subscriber

            val executionQuery = ExecutionInput.newExecutionInput()
                .query(payload.query)
                .variables(payload.variables ?: mapOf())
                .operationName(payload.operationName)
                .build()

            Flowable.defer {
                RequestIdFilter.withRequestId(loggingRequestId) {
                    val result = graphQL.execute(executionQuery)
                    // check if the graphQL result is a Publisher
                    val publisherData = try {
                        result.getData<Publisher<ExecutionResult>>()
                    } catch (e: Exception) {
                        null
                    }
                    // if result is itself a Publisher, subscribe to that
                    // if it's not, subscribe to a Single wrapping the result
                    publisherData ?: Single.just(result).toFlowable()
                }
            }
                .subscribeOn(scheduler)
                .subscribe(subscriber)
        }
    }

    /**
     * Stop a query identified by messageId
     */
    private fun handleQueryStop(message: OperationMessage<*>) {
        runningQueryLock.withLock {
            check(runningQuerySubscriber?.messageId == message.id) {
                "Tried to cancel query ${message.id} but it's not running"
            }
            log.info("Query ${message.id} cancelled by user")
            runningQuerySubscriber?.unsubscribe()
            runningQuerySubscriber = null
            sendMessage(OperationType.GQL_COMPLETE, message.id)
        }
    }

    /**
     * Respond to connection initialization by acknowledging
     */
    private fun handleConnectionInit(message: OperationMessage<*>) {
        sendMessage(OperationType.GQL_CONNECTION_ACK, message.id)
        keepAliveLock.withLock {
            if (this.keepAliveInterval == null) {
                sendMessage(OperationType.GQL_CONNECTION_KEEP_ALIVE, message.id)
                val keepAliveCallbacks = KeepAliveCallbacks(this, message.id)
                this.keepAliveInterval = Observable.interval(
                    this.keepAliveIntervalSeconds,
                    TimeUnit.SECONDS,
                    Schedulers.io()
                )
                    .subscribe(keepAliveCallbacks::onInterval, keepAliveCallbacks::onError)
            }
        }
    }

    /**
     * Stop any running query and terminate the connection
     */
    private fun handleConnectionTermination(statusCode: Int, reason: String?) {
        runningQueryLock.withLock {
            log.info("Query ${runningQuerySubscriber?.messageId} cancelled due to disconnect")
            runningQuerySubscriber?.unsubscribe()
            runningQuerySubscriber = null
            keepAliveLock.withLock {
                keepAliveInterval?.dispose()
                keepAliveInterval = null
                if (session != null) {
                    session.close(statusCode, reason)
                }
            }
        }
    }

    /**
     * Error out
     */
    private fun handleUnknownMessage(message: OperationMessage<*>) {
        sendMessage(OperationType.GQL_CONNECTION_ERROR, message.id)
    }

    /**
     * Receive a message from the websocket and process it according to its type
     */
    override fun onWebSocketText(message: String) {
        try {
            RequestIdFilter.withRequestId {
                val operation = objectMapper.readValue<OperationMessage<*>>(message)
                try {
                    when (operation.type) {
                        OperationType.GQL_START -> handleQueryStart(message)
                        OperationType.GQL_CONNECTION_INIT -> handleConnectionInit(operation)
                        OperationType.GQL_STOP -> handleQueryStop(operation)
                        OperationType.GQL_CONNECTION_TERMINATE -> handleConnectionTermination(
                            StatusCode.NORMAL,
                            "Termination Requested"
                        )
                        else -> handleUnknownMessage(operation)
                    }
                } catch (e: Exception) {
                    log.error("Error processing parsed message: ${e.message}", e)
                    sendMessage(OperationType.GQL_ERROR, operation.id, e.message)
                }
            }
        } catch (e: Exception) {
            log.error("Error parsing message: ${e.message}", e)
            sendMessage(OperationType.GQL_ERROR, null, e.message)
        }
    }

    /**
     * Make sure we clean up running things if the websocket closes
     */
    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        super.onWebSocketClose(statusCode, reason)
        handleConnectionTermination(statusCode, reason)
    }

    /**
     * On error, close connection
     */
    override fun onWebSocketError(cause: Throwable) {
        handleConnectionTermination(StatusCode.SERVER_ERROR, cause.message)
        log.error("Error in websocket: ${cause.message}", cause)
    }

    /**
     * Allow a running query to notify the socket that it has completed, with or without error
     */
    internal open fun onQueryFinished(
        subscriber: GraphQLQuerySubscriber,
        resultCount: Int,
        cause: Throwable? = null
    ) {
        runningQueryLock.withLock {
            check(runningQuerySubscriber == subscriber) {
                "Query ${subscriber.messageId} but we don't think it's running"
            }
            if (cause == null) {
                log.info("Query ${subscriber.messageId} finished with $resultCount results")
                sendMessage(OperationType.GQL_COMPLETE, subscriber.messageId)
            } else {
                log.error(
                    "Error in subscription ${subscriber.messageId} " +
                            "after $resultCount results: ${cause.message}",
                    cause
                )
                sendMessage(OperationType.GQL_ERROR, subscriber.messageId, cause.message)
            }
            this.runningQuerySubscriber = null
        }
    }

    /**
     * Convenience method for writing an [OperationMessage] back to the client in json format
     */
    internal open fun sendMessage(message: OperationMessage<*>) {
        remoteLock.withLock {
            remote.sendString(objectWriter.writeValueAsString(message))
        }
    }

    /**
     * Convenience method for writing the components of an [OperationMessage] back to the client in json format
     */
    internal fun <T : Any> sendMessage(type: OperationType<T>, id: String?, payload: T? = null) {
        sendMessage(OperationMessage(type, id, payload))
    }
}
