package com.trib3.graphql.websocket

import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.GraphQLRequest
import com.trib3.server.filters.RequestIdFilter
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subscribers.ResourceSubscriber
import mu.KotlinLogging
import org.eclipse.jetty.websocket.api.StatusCode
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import java.util.concurrent.TimeUnit
import javax.annotation.Nullable

private val log = KotlinLogging.logger {}

/**
 * rx [Subscriber] that listens for events on coming from the WebSocket managed
 * by a [GraphQLWebSocketAdapter], and implements the apollo graphql-ws protocol
 * from https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 *
 * Handling some WebSocket events results in new rx subscriptions (eg, starting a query or
 * a keepalive timer).  The handlers for those subscriptions must inject back into the
 * original subscription via `adapter.emitter` if they need to modify the subscriber's state
 * or send data to the WebSocket client.
 */
class GraphQLWebSocketSubscriber(
    @Nullable val graphQL: GraphQL?,
    val graphQLConfig: GraphQLConfig,
    val scheduler: Scheduler = Schedulers.io(), // default to io scheduler for actual work
    val keepAliveScheduler: Scheduler = Schedulers.computation() // default to computation for the KA interval
) : ResourceSubscriber<OperationMessage<*>>() {
    internal lateinit var adapter: GraphQLWebSocketAdapter
    private var keepAlive: Disposable? = null
    private val queries = mutableMapOf<String, Disposable>()

    override fun onStart() {
        // do nothing -- don't request events until `afterSubscribed()` is called, when `adapter` will be set
    }

    /**
     * Initiate requesting events from the stream after subscribed and sets up the
     * subscription to dispose on cancellation
     */
    fun afterSubscribed() {
        adapter.emitter.setCancellable {
            log.debug("Cancelling websocket connection flowable")
            adapter.session?.close(StatusCode.NORMAL, "Termination Requested")
            dispose()
        }
        request(Long.MAX_VALUE)
    }

    /**
     * When the websocket subscription is complete (ie, close of session), dispose of any downstream subscriptions
     */
    override fun onComplete() {
        log.debug("WebSocket connection subscription complete")
        dispose()
    }

    /**
     * Upon receiving WebSocket events, process them as appropriate
     */
    override fun onNext(message: OperationMessage<*>) {
        RequestIdFilter.withRequestId(message.id) {
            try {
                log.trace("WebSocket connection subscription processing $message")
                when (message.type) {
                    // Connection control messages from the client
                    OperationType.GQL_CONNECTION_INIT -> handleConnectionInit(message)
                    OperationType.GQL_CONNECTION_TERMINATE -> handleConnectionTerminate(message)

                    // Query control messages from the client
                    OperationType.GQL_START -> handleQueryStart(message)
                    OperationType.GQL_STOP -> handleQueryStop(message)

                    // Query finished messages from downstream subscriptions
                    OperationType.GQL_COMPLETE,
                    OperationType.GQL_ERROR -> {
                        log.info("Query ${message.id} completed: $message")
                        queries.remove(message.id)?.dispose()
                        handleClientBoundMessage(message)
                    }

                    // Any client bound messages from downstream subscriptions
                    OperationType.GQL_CONNECTION_ERROR,
                    OperationType.GQL_CONNECTION_ACK,
                    OperationType.GQL_DATA,
                    OperationType.GQL_CONNECTION_KEEP_ALIVE -> handleClientBoundMessage(
                        message
                    )

                    // Unknown message, let the client know
                    else -> handleClientBoundMessage(
                        OperationMessage(
                            OperationType.GQL_ERROR,
                            message.id,
                            "Unknown message type"
                        )
                    )
                }
            } catch (error: Throwable) {
                log.error("Error processing message ${error.message}", error)
                adapter.sendMessage(OperationType.GQL_ERROR, message.id, error.message)
            }
        }
    }

    /**
     * Process an [OperationType.GQL_CONNECTION_INIT] message.  If the connection
     * has already initialized, send an error back to the client.  Otherwise acknowledge
     * the connection and start a keepalive timer.
     */
    private fun handleConnectionInit(message: OperationMessage<*>) {
        if (keepAlive == null) {
            adapter.sendMessage(OperationType.GQL_CONNECTION_ACK, message.id)
            adapter.sendMessage(OperationType.GQL_CONNECTION_KEEP_ALIVE, message.id)
            val newKeepAlive = Flowable.interval(
                graphQLConfig.keepAliveIntervalSeconds,
                TimeUnit.SECONDS,
                keepAliveScheduler
            ).subscribe {
                log.trace("WebSocket connection keepalive ping $it")
                adapter.emitter.onNext(
                    OperationMessage(
                        OperationType.GQL_CONNECTION_KEEP_ALIVE,
                        message.id
                    )
                )
            }
            keepAlive = newKeepAlive
            add(newKeepAlive)
        } else {
            adapter.sendMessage(OperationType.GQL_CONNECTION_ERROR, message.id, "Already connected!")
        }
    }

    /**
     * Process an [OperationType.GQL_CONNECTION_TERMINATE] message.  Cancelling the subscription
     * will dispose of any downstream subscriptions and close the WebSocket (see [afterSubscribed])
     */
    private fun handleConnectionTerminate(message: OperationMessage<*>) {
        log.info("WebSocket connection termination requested by message ${message.id}!")
        dispose()
    }

    /**
     * Process an [OperationType.GQL_START] message.  Executes the graphQL query in a downstream
     * subscriber which will asynchronously emit new events as the query returns data or errors.
     * Tracks running queries by client specified id, and only allows one running query for a given id.
     */
    private fun handleQueryStart(message: OperationMessage<*>) {
        val messageId = message.id
        check(messageId != null) {
            "Must pass a message id to start a query"
        }
        check(!queries.containsKey(messageId)) {
            "Query with id $messageId already running!"
        }
        check(message.payload is GraphQLRequest) {
            "Invalid payload for query"
        }
        check(graphQL != null) {
            "graphQL not configured!"
        }
        val payload = message.payload as GraphQLRequest
        val executionQuery = ExecutionInput.newExecutionInput()
            .query(payload.query)
            .variables(payload.variables ?: mapOf())
            .operationName(payload.operationName)
            .build()
        val flowable = Flowable.defer {
            RequestIdFilter.withRequestId(messageId) {
                val result = graphQL.execute(executionQuery)
                // check if the graphQL result is a Publisher
                val publisherData = try {
                    result.getData<Publisher<ExecutionResult>>()
                } catch (e: Exception) {
                    null
                }
                // if result is itself a Publisher, subscribe to that
                // if it's not, subscribe to just the result
                publisherData ?: Flowable.just(result)
            }
        }.subscribeOn(scheduler).publish()
        val query = flowable.subscribe(
            {
                adapter.emitter.onNext(
                    OperationMessage(
                        OperationType.GQL_DATA,
                        messageId,
                        it
                    )
                )
            },
            {
                onDownstreamError(message.id, it)
            },
            {
                adapter.emitter.onNext(
                    OperationMessage(
                        OperationType.GQL_COMPLETE,
                        messageId
                    )
                )
            }
        )
        add(query)
        queries[messageId] = query
        flowable.connect()
    }

    /**
     * Process an [OperationType.GQL_STOP] message.  If the query specified by id is running, will dispose of
     * the downstream subscription and notify the client of completion, else will notify the client of an error.
     *
     * A stopped query may return data packets after completion if the data was already in flight to the client
     * before the query was stopped.
     */
    private fun handleQueryStop(message: OperationMessage<*>) {
        val toStop = queries[message.id]
        if (toStop != null) {
            toStop.dispose()
            handleClientBoundMessage(OperationMessage(OperationType.GQL_COMPLETE, message.id))
            queries.remove(message.id)
        } else {
            handleClientBoundMessage(OperationMessage(OperationType.GQL_ERROR, message.id, "Query not running"))
        }
    }

    /**
     * Send the [message] to the client via the subscription's [adapter]
     */
    private fun handleClientBoundMessage(message: OperationMessage<*>) {
        log.trace("WebSocket connection sending $message")
        adapter.sendMessage(message)
    }

    /**
     * On unrecoverable errors, dispose of all resources and error out the WebSocket
     */
    override fun onError(cause: Throwable) {
        log.info("WebSocket connection subscription error ${cause.message}", cause)
        dispose()
        adapter.onWebSocketError(cause)
    }

    /**
     * On errors from downstream consumers, send an error message to the client, but leave
     * the WebSocket open for further use (ie, recoverable errors shouldn't route to [onError]).
     */
    private fun onDownstreamError(messageId: String?, cause: Throwable) {
        RequestIdFilter.withRequestId(messageId) {
            log.error("Downstream error ${cause.message}", cause)
            adapter.emitter.onNext(
                OperationMessage(
                    OperationType.GQL_ERROR,
                    messageId,
                    cause.message
                )
            )
        }
    }
}
