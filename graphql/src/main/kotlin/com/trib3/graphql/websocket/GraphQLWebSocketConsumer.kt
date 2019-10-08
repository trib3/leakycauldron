package com.trib3.graphql.websocket

import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.GraphQLRequest
import com.trib3.server.filters.RequestIdFilter
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.yield
import mu.KotlinLogging
import org.eclipse.jetty.websocket.api.StatusCode
import org.reactivestreams.Publisher
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.Nullable

private val log = KotlinLogging.logger {}

/**
 * Coroutine based consumer that listens for events on coming from the WebSocket managed
 * by a [GraphQLWebSocketAdapter], and implements the apollo graphql-ws protocol
 * from https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 *
 * Handling some WebSocket events launches a child coroutine (eg, starting a query or
 * a keepalive timer).  The handlers for those subscriptions must inject back into the
 * original coroutine via `queueMessage` if they need to modify the subscriber's state
 * or send data to the WebSocket client.
 */
@UseExperimental(ExperimentalCoroutinesApi::class)
class GraphQLWebSocketConsumer(
    @Nullable val graphQL: GraphQL?,
    val graphQLConfig: GraphQLConfig,
    val channel: Channel<OperationMessage<*>>,
    val adapter: GraphQLWebSocketAdapter,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO, // default to io dispatcher for actual work
    val keepAliveDispatcher: CoroutineDispatcher = Dispatchers.Default // default to default for the KA interval
) {
    private var keepAliveStarted = false // only allow one keepalive coroutine to launch
    private val jobs = ConcurrentHashMap<String, Job>() // will contain child queries that are currently running

    /**
     * Launches a coroutine to consume WebSocket API events from
     * the [channel]
     */
    fun launchConsumer() {
        // use GlobalScope to release the calling thread
        GlobalScope.launch(dispatcher) {
            for (message in channel) {
                handleMessage(message, this)
            }
            // fully consumed the channel, close any children that are still running
            cancel()
        }
    }

    /**
     * Upon receiving WebSocket events, process them as appropriate
     */
    suspend fun handleMessage(message: OperationMessage<*>, scope: CoroutineScope) {
        RequestIdFilter.withRequestId(message.id) {
            try {
                log.trace("WebSocket connection subscription processing $message")
                when (message.type) {
                    // Connection control messages from the client
                    OperationType.GQL_CONNECTION_INIT -> handleConnectionInit(message, scope)
                    OperationType.GQL_CONNECTION_TERMINATE -> handleConnectionTerminate(message, scope)

                    // Query control messages from the client
                    OperationType.GQL_START -> handleQueryStart(message, scope)
                    OperationType.GQL_STOP -> handleQueryStop(message)

                    // Query finished messages from child coroutines
                    OperationType.GQL_COMPLETE,
                    OperationType.GQL_ERROR -> {
                        log.info("Query ${message.id} completed: $message")
                        if (message.id != null) {
                            jobs.remove(message.id)?.cancel()
                        }
                        handleClientBoundMessage(message)
                    }

                    // Any client bound messages from child coroutines
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
    private suspend fun handleConnectionInit(message: OperationMessage<*>, scope: CoroutineScope) {
        if (!keepAliveStarted) {
            adapter.sendMessage(OperationType.GQL_CONNECTION_ACK, message.id)
            adapter.sendMessage(OperationType.GQL_CONNECTION_KEEP_ALIVE, message.id)
            keepAliveStarted = true
            scope.launch(keepAliveDispatcher + MDCContext()) {
                while (true) {
                    delay(graphQLConfig.keepAliveIntervalSeconds * 1000)
                    log.trace("WebSocket connection keepalive ping")
                    queueMessage(
                        OperationMessage(
                            OperationType.GQL_CONNECTION_KEEP_ALIVE,
                            message.id
                        )
                    )
                }
            }
        } else {
            adapter.sendMessage(OperationType.GQL_CONNECTION_ERROR, message.id, "Already connected!")
        }
    }

    /**
     * Process an [OperationType.GQL_CONNECTION_TERMINATE] message.  Cancelling the connection's coroutine
     * will dispose of any child coroutines and close the WebSocket
     */
    private fun handleConnectionTerminate(message: OperationMessage<*>, scope: CoroutineScope) {
        log.info("WebSocket connection termination requested by message ${message.id}!")
        adapter.session?.close(StatusCode.NORMAL, "Termination Requested")
        scope.cancel()
    }

    /**
     * Process an [OperationType.GQL_START] message.  Executes the graphQL query in a child
     * coroutine which will asynchronously emit new events as the query returns data or errors.
     * Tracks running queries by client specified id, and only allows one running query for a given id.
     */
    private suspend fun handleQueryStart(message: OperationMessage<*>, scope: CoroutineScope) {
        val messageId = message.id
        check(messageId != null) {
            "Must pass a message id to start a query"
        }
        check(!jobs.containsKey(messageId)) {
            "Query with id $messageId already running!"
        }
        check(message.payload is GraphQLRequest) {
            "Invalid payload for query"
        }
        check(graphQL != null) {
            "graphQL not configured!"
        }
        val payload = message.payload
        val executionQuery = ExecutionInput.newExecutionInput()
            .query(payload.query)
            .variables(payload.variables ?: mapOf())
            .operationName(payload.operationName)
            .build()

        val job = scope.launch(dispatcher + MDCContext()) {
            try {
                val result = graphQL.execute(executionQuery)
                // if result data is a Publisher, collect it as a flow
                // if it's not, just collect the result itself
                val flow = try {
                    result.getData<Publisher<ExecutionResult>>().asFlow()
                } catch (e: Exception) {
                    flowOf(result)
                }
                flow.onEach {
                    yield() // allow for cancellations to abort the coroutine
                    queueMessage(OperationMessage(OperationType.GQL_DATA, messageId, it))
                }.catch {
                    onChildError(messageId, it)
                }.onCompletion { maybeException ->
                    // Only send complete if there's no exception and we still think we're processing this query
                    if (maybeException == null && jobs[messageId] != null) {
                        queueMessage(OperationMessage(OperationType.GQL_COMPLETE, messageId))
                    }
                }.collect()
            } catch (e: Exception) {
                onChildError(messageId, e)
            }
        }
        jobs[messageId] = job
    }

    /**
     * Process an [OperationType.GQL_STOP] message.  If the query specified by id is running, will cancel of
     * the child coroutine and notify the client of completion, else will notify the client of an error.
     *
     * A stopped query may return data packets after completion if the data was already in flight to the client
     * before the query was stopped.
     */
    private fun handleQueryStop(message: OperationMessage<*>) {
        val toStop = jobs[message.id]
        if (toStop != null) {
            log.info("Stopping WebSocket query: ${message.id}!")
            toStop.cancel()
            handleClientBoundMessage(OperationMessage(OperationType.GQL_COMPLETE, message.id))
            jobs.remove(message.id)
        } else {
            handleClientBoundMessage(OperationMessage(OperationType.GQL_ERROR, message.id, "Query not running"))
        }
    }

    /**
     * Send the [message] to the client via the [adapter]
     */
    private fun handleClientBoundMessage(message: OperationMessage<*>) {
        log.trace("WebSocket connection sending $message")
        adapter.sendMessage(message)
    }

    /**
     * On errors from child coroutines, send an error message to the client, but leave
     * the WebSocket open for further use (ie, recoverable errors shouldn't get thrown).
     */
    private suspend fun onChildError(messageId: String?, cause: Throwable) {
        log.error("Downstream error ${cause.message}", cause)
        queueMessage(
            OperationMessage(
                OperationType.GQL_ERROR,
                messageId,
                cause.message
            )
        )
    }

    /**
     * Send the [message] to the [channel] to be processed in the main coroutine
     */
    private suspend fun queueMessage(message: OperationMessage<*>) {
        if (!channel.isClosedForSend) {
            channel.send(message)
        }
    }
}
