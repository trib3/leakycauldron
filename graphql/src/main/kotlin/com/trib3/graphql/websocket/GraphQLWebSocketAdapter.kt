package com.trib3.graphql.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.eclipse.jetty.websocket.api.Callback
import org.eclipse.jetty.websocket.api.Session

private val log = KotlinLogging.logger {}

/**
 * [WebSocketAdapter] implementation that bridges incoming WebSocket events into
 * a coroutine [Channel] to be handled by a consumer, and provides access
 * to the [remote] for sending messages to the client.
 */
open class GraphQLWebSocketAdapter(
    val subProtocol: GraphQLWebSocketSubProtocol,
    val channel: Channel<OperationMessage<*>>,
    val objectMapper: ObjectMapper,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Session.Listener.AutoDemanding,
    CoroutineScope by CoroutineScope(dispatcher) {
    val objectWriter = objectMapper.writerWithDefaultPrettyPrinter()!!
    var session: Session? = null

    companion object {
        private val CLIENT_SOURCED_MESSAGES =
            listOf(
                OperationType.GQL_CONNECTION_INIT,
                OperationType.GQL_START,
                OperationType.GQL_STOP,
                OperationType.GQL_CONNECTION_TERMINATE,
                OperationType.GQL_PING,
                OperationType.GQL_PONG,
            )
    }

    override fun onWebSocketOpen(session: Session?) {
        this.session = session
    }

    /**
     * Parse incoming messages as [OperationMessage]s, then send them to the channel consumer
     */
    override fun onWebSocketText(message: String) =
        runBlocking {
            try {
                val operation =
                    subProtocol.getClientToServerMessage(
                        objectMapper.readValue<OperationMessage<*>>(message),
                    )
                // Only send client->server messages downstream, otherwise kill the socket per graphql-ws protocol
                if (operation.type in CLIENT_SOURCED_MESSAGES) {
                    channel.send(operation)
                } else {
                    subProtocol.onInvalidMessage(operation.id, message, this@GraphQLWebSocketAdapter)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                log.error(error) { "Error parsing message: ${error.message}" }
                subProtocol.onInvalidMessage(null, message, this@GraphQLWebSocketAdapter)
            }
        }

    /**
     * Notify channel that the stream is finished
     */
    override fun onWebSocketClose(
        statusCode: Int,
        reason: String?,
        callback: Callback?,
    ) {
        val msg = "WebSocket close $statusCode $reason"
        log.debug { msg }
        super.onWebSocketClose(statusCode, reason, callback)
        channel.close()
        callback?.succeed()
        cancel(msg)
    }

    /**
     * Just log the error, and rely on the [onWebSocketClose] callback to clean up
     */
    override fun onWebSocketError(cause: Throwable) {
        log.error(cause) { "WebSocket error ${cause.message}" }
    }

    /**
     * Convenience method for writing an [OperationMessage] back to the client in json format
     * Must be called from the Subscriber's observation context
     */
    internal fun sendMessage(message: OperationMessage<*>) {
        session?.sendText(objectWriter.writeValueAsString(subProtocol.getServerToClientMessage(message)), null)
    }

    /**
     * Convenience method for writing the components of an [OperationMessage] back to the client in json format
     * Must be called from the Subscriber's observation context
     */
    internal fun <T : Any> sendMessage(
        type: OperationType<T>,
        id: String?,
        payload: T? = null,
    ) {
        sendMessage(OperationMessage(type, id, payload))
    }
}
