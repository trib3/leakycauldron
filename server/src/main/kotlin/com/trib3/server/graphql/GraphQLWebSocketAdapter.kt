package com.trib3.server.graphql

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.reactivex.FlowableEmitter
import mu.KotlinLogging
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.api.WebSocketAdapter

private val log = KotlinLogging.logger {}

/**
 * [WebSocketAdapter] implementation that bridges incoming WebSocket events into
 * the rx [FlowableEmitter] to be handled by an rx subscriber, and provides access
 * to the [remote] for sending messages to the client.
 */
class GraphQLWebSocketAdapter(
    val emitter: FlowableEmitter<OperationMessage<*>>,
    val objectMapper: ObjectMapper
) : WebSocketAdapter() {
    val objectWriter = objectMapper.writerWithDefaultPrettyPrinter()!!

    companion object {
        private val CLIENT_SOURCED_MESSAGES = listOf(
            OperationType.GQL_CONNECTION_INIT,
            OperationType.GQL_START,
            OperationType.GQL_STOP,
            OperationType.GQL_CONNECTION_TERMINATE
        )
    }

    /**
     * Parse incoming messages as [OperationMessage]s, then send them to rx
     */
    override fun onWebSocketText(message: String) {
        try {
            val operation = objectMapper.readValue<OperationMessage<*>>(message)
            // Only send client->server messages downstream, otherwise queue an error to be sent back
            if (operation.type in CLIENT_SOURCED_MESSAGES) {
                emitter.onNext(operation)
            } else {
                emitter.onNext(
                    OperationMessage(
                        OperationType.GQL_ERROR,
                        operation.id,
                        "Invalid message type ${operation.type}"
                    )
                )
            }
        } catch (error: Throwable) {
            // Don't kill the socket because of a bad message, just queue an error to be sent back
            log.error("Error parsing message: ${error.message}", error)
            emitter.onNext(
                OperationMessage(
                    OperationType.GQL_ERROR,
                    null,
                    error.message
                )
            )
        }
    }

    /**
     * Notify rx that the stream is finished
     */
    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        log.debug("WebSocket close $statusCode $reason")
        super.onWebSocketClose(statusCode, reason)
        emitter.onComplete()
    }

    /**
     * notify rx the stream has errored unrecoverably
     */
    override fun onWebSocketError(cause: Throwable) {
        log.error("WebSocket error ${cause.message}", cause)
        emitter.onError(cause)
        session?.close(StatusCode.SERVER_ERROR, cause.message)
    }

    /**
     * Convenience method for writing an [OperationMessage] back to the client in json format
     * Must be called from the Subscriber's observation context
     */
    internal fun sendMessage(message: OperationMessage<*>) {
        remote?.sendString(objectWriter.writeValueAsString(message))
    }

    /**
     * Convenience method for writing the components of an [OperationMessage] back to the client in json format
     * Must be called from the Subscriber's observation context
     */
    internal fun <T : Any> sendMessage(type: OperationType<T>, id: String?, payload: T? = null) {
        sendMessage(OperationMessage(type, id, payload))
    }
}
