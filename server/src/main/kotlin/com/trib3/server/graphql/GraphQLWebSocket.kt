package com.trib3.server.graphql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.trib3.server.filters.RequestIdFilter
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import mu.KotlinLogging
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.reactivestreams.Publisher
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val log = KotlinLogging.logger { }

/**
 * A [WebSocketAdapter] that accepts a GraphQL query and executes it.  Returns a GraphQL ExecutionResult
 * for regular operations, but if a subscription operation returns a [Publisher], will stream an
 * ExecutionResult over the websocket for each published result.
 *
 * Implements https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */
open class GraphQLWebSocket(
    val graphQL: GraphQL?,
    val objectMapper: ObjectMapper
) : WebSocketAdapter() {
    val objectWriter = objectMapper.writerWithDefaultPrettyPrinter()

    private val runningQueryLock = ReentrantLock()
    @Volatile
    private var runningQuerySubscriber: GraphQLQuerySubscriber? = null

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
            val result = graphQL.execute(
                ExecutionInput.newExecutionInput()
                    .query(payload.query)
                    .variables(payload.variables ?: mapOf())
                    .operationName(payload.operationName)
                    .build()
            )

            val publisherData = try {
                result.getData<Publisher<ExecutionResult>>()
            } catch (e: Exception) {
                null
            }
            if (publisherData == null) {
                sendMessage(OperationType.GQL_DATA, message.id, result)
                sendMessage(OperationType.GQL_COMPLETE, message.id)
            } else {
                val loggingRequestId = RequestIdFilter.getRequestId()!!
                val subscriber = GraphQLQuerySubscriber(this, message.id, loggingRequestId)
                this.runningQuerySubscriber = subscriber
                publisherData.subscribe(subscriber)
            }
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
    }

    /**
     * Stop any running query and terminate the connection
     */
    private fun handleConnectionTermination() {
        runningQueryLock.withLock {
            runningQuerySubscriber?.unsubscribe()
            runningQuerySubscriber = null
            session.close(StatusCode.NORMAL, "Termination Requested")
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
                        OperationType.GQL_CONNECTION_TERMINATE -> handleConnectionTermination()
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
     * On error, close connection
     */
    override fun onWebSocketError(cause: Throwable) {
        session.close(StatusCode.SERVER_ERROR, cause.message)
        log.error("Error in websocket: ${cause.message}", cause)
    }

    /**
     * Allow a running query to notify the socket that it has completed, with or without error
     */
    internal open fun onQueryFinished(subscriber: GraphQLQuerySubscriber, cause: Throwable? = null) {
        runningQueryLock.withLock {
            check(runningQuerySubscriber == subscriber) {
                "Query ${subscriber.messageId} but we don't think it's running"
            }
            if (cause == null) {
                log.info("Query ${subscriber.messageId} finished")
                sendMessage(OperationType.GQL_COMPLETE, subscriber.messageId)
            } else {
                log.error("Error in subscription ${subscriber.messageId}: ${cause.message}", cause)
                sendMessage(OperationType.GQL_ERROR, subscriber.messageId, cause.message)
            }
            this.runningQuerySubscriber = null
        }
    }

    /**
     * Convenience method for writing an [OperationMessage] back to the client in json format
     */
    internal open fun sendMessage(message: OperationMessage<*>) {
        remote.sendString(objectWriter.writeValueAsString(message))
    }

    /**
     * Convenience method for writing the components of an [OperationMessage] back to the client in json format
     */
    internal fun <T : Any> sendMessage(type: OperationType<T>, id: String?, payload: T? = null) {
        sendMessage(OperationMessage(type, id, payload))
    }
}
