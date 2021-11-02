package com.trib3.graphql.websocket

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.trib3.graphql.execution.GraphQLRequest
import com.trib3.graphql.execution.MessageGraphQLError
import graphql.ExecutionResult
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.StatusCode
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Encapsulates the different behaviors in the apollo vs/ graphql-ws protocols.
 * Maps equivalent messages (eg "start" vs "subscribe"), and provides callbacks
 * for different behaviors (eg end an "error" message vs/ close the connection).
 *
 * The WebSocket consumer always works using apollo messages internally, so
 * the adapter is responsible for translating from/to graphql-ws messages upon
 * receipt/send events.
 */
enum class GraphQLWebSocketSubProtocol(
    val subProtocol: String,
    messageMapping: Map<OperationType<*>, OperationType<*>>,
    private val onInvalidMessageCallback: (String?, String, GraphQLWebSocketAdapter) -> Unit,
    private val onDuplicateQueryCallback: (OperationMessage<*>, GraphQLWebSocketAdapter) -> Unit,
    private val onDuplicateInitCallback: (OperationMessage<*>, GraphQLWebSocketAdapter) -> Unit
) {
    APOLLO_PROTOCOL(
        "graphql-ws",
        mapOf(),
        { messageId, messageBody, adapter ->
            adapter.sendMessage(
                OperationMessage(
                    OperationType.GQL_ERROR, messageId,
                    listOf(MessageGraphQLError("Invalid message `$messageBody`"))
                )
            )
        },
        { message, adapter ->
            adapter.sendMessage(
                OperationMessage(
                    OperationType.GQL_ERROR,
                    message.id,
                    listOf(MessageGraphQLError("Query with id ${message.id} already running!"))
                )
            )
        },
        { message, adapter ->
            adapter.sendMessage(
                OperationMessage(
                    OperationType.GQL_CONNECTION_ERROR,
                    message.id,
                    "Already connected!"
                )
            )
        }
    ),
    GRAPHQL_WS_PROTOCOL(
        "graphql-transport-ws",
        mapOf(
            OperationType.GQL_START to OperationType.GQL_SUBSCRIBE,
            OperationType.GQL_DATA to OperationType.GQL_NEXT,
            OperationType.GQL_STOP to OperationType.GQL_COMPLETE,
            OperationType.GQL_CONNECTION_KEEP_ALIVE to OperationType.GQL_PONG
        ),
        { msgId, msgBody, adapter ->
            adapter.session?.close(
                GraphQLWebSocketCloseReason.INVALID_MESSAGE.code,
                GraphQLWebSocketCloseReason.INVALID_MESSAGE.description.replace("<id>", msgId ?: "")
                    .replace("<body>", msgBody)
            )
        },
        { message, adapter ->
            adapter.session?.close(
                GraphQLWebSocketCloseReason.MULTIPLE_SUBSCRIBER.code,
                GraphQLWebSocketCloseReason.MULTIPLE_SUBSCRIBER.description.replace(
                    "<unique-operation-id>",
                    message.id ?: ""
                )
            )
        },
        { _, adapter ->
            adapter.session?.close(GraphQLWebSocketCloseReason.MULTIPLE_INIT)
        }
    );

    private val apolloToGraphQlWsMapping = messageMapping.entries.associate { it.key.type to it.value.type }

    private val graphQlWsToApolloMapping = apolloToGraphQlWsMapping.entries.associate {
        it.value to it.key
    }.filter {
        // don't map PONG to KEEPALIVE even though we map outgoing KEEPALIVE messages to PONGs
        it.key != OperationType.GQL_PONG.type
    }

    private fun <T : Any> getMessage(message: OperationMessage<T>, mapping: Map<String, String>): OperationMessage<T> {
        return if (!mapping.containsKey(message.type?.type)) {
            message
        } else {
            message.copy(type = message.type?.copy(type = mapping.getValue(message.type.type)))
        }
    }

    /**
     * Before sending a message to the client, map to the appropriate protocol message
     */
    fun <T : Any> getServerToClientMessage(message: OperationMessage<T>): OperationMessage<T> {
        return getMessage(message, apolloToGraphQlWsMapping)
    }

    /**
     * Upon receiving a message from the client, map from appropriate protocol message to an apollo
     * message for internal consumption
     */
    fun <T : Any> getClientToServerMessage(message: OperationMessage<T>): OperationMessage<T> {
        return getMessage(message, graphQlWsToApolloMapping)
    }

    /**
     * graphql-ws closes the connection upon receiving an invalid message, while apollo just sends
     * an error message in response.
     */
    fun onInvalidMessage(messageId: String?, messageBody: String, adapter: GraphQLWebSocketAdapter) {
        this.onInvalidMessageCallback(messageId, messageBody, adapter)
    }

    /**
     * graphql-ws closes the connection upon receiving a duplicate subscription, while apollo just sends
     * an error message in response.
     */
    fun onDuplicateQuery(message: OperationMessage<*>, adapter: GraphQLWebSocketAdapter) {
        this.onDuplicateQueryCallback(message, adapter)
    }

    /**
     * graphql-ws closes the connection upon receiving duplicate connection_init requests, while apollo
     * just sends an error message in response.
     */
    fun onDuplicateInit(message: OperationMessage<*>, adapter: GraphQLWebSocketAdapter) {
        this.onDuplicateInitCallback(message, adapter)
    }
}

/**
 * Enum of websocket closure reasons from the graphql-ws protocol specification
 */
@Suppress("MagicNumber") // constructor values are consts, just put the numbers in instead of declaring const vals.
enum class GraphQLWebSocketCloseReason(val code: Int, val description: String) {
    NORMAL(StatusCode.NORMAL, "Normal Closure"),
    INVALID_MESSAGE(4400, "Invalid Message with id `<id>`: `<body>`"),
    UNAUTHORIZED(4401, "Unauthorized"),
    TIMEOUT_INIT(4408, "Connection initialisation timeout"),
    MULTIPLE_SUBSCRIBER(4409, "Subscriber for <unique-operation-id> already exists"),
    MULTIPLE_INIT(4429, "Too many initialisation requests")
    ;
}

/**
 * Extension method to close the WebSocket session using a [GraphQLWebSocketCloseReason] instead of
 * code/description pairs
 */
fun Session.close(reason: GraphQLWebSocketCloseReason) {
    this.close(reason.code, reason.description)
}

/**
 * Model for a message in the graphql websocket protocol.  Supports messages for either
 * the apollo or graphql-ws protocols
 *
 * https://github.com/apollographql/subscriptions-transport-ws/blob/HEAD/PROTOCOL.md
 * https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md
 */
data class OperationMessage<T : Any>(
    val type: OperationType<T>?,
    val id: String?,

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "type"
    )
    @JsonSubTypes(
        Type(name = "start", value = GraphQLRequest::class),
        Type(name = "subscribe", value = GraphQLRequest::class),
        Type(name = "data", value = ExecutionResult::class),
        Type(name = "next", value = ExecutionResult::class),
        Type(name = "error", value = List::class),
        Type(name = "connection_init", value = Map::class),
        Type(name = "connection_terminate", value = Nothing::class),
        Type(name = "connection_ack", value = Map::class),
        Type(name = "connection_error", value = String::class),
        Type(name = "stop", value = Nothing::class),
        Type(name = "complete", value = Nothing::class),
        Type(name = "ka", value = Nothing::class),
        Type(name = "ping", value = Map::class),
        Type(name = "pong", value = Map::class),
    )
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val payload: T? = null
)

/**
 * Captures the mapping from operation type to payload type for the messages in the protocol
 */
data class OperationType<T : Any>(
    @get:JsonValue val type: String,
    @JsonIgnore
    val payloadType: KClass<T>
) {
    companion object {
        // client -> server
        val GQL_CONNECTION_INIT = OperationType("connection_init", Map::class) // shared
        val GQL_START = OperationType("start", GraphQLRequest::class) // apollo
        val GQL_SUBSCRIBE = OperationType("subscribe", GraphQLRequest::class) // graphql-ws
        val GQL_STOP = OperationType("stop", Nothing::class) // apollo-only, graphql-ws uses COMPLETE
        val GQL_CONNECTION_TERMINATE = OperationType("connection_terminate", Nothing::class) // apollo-only

        // server -> client
        val GQL_CONNECTION_ERROR = OperationType("connection_error", String::class) // apollo-only
        val GQL_CONNECTION_ACK = OperationType("connection_ack", Map::class) // shared
        val GQL_DATA = OperationType("data", ExecutionResult::class) // apollo
        val GQL_NEXT = OperationType("next", ExecutionResult::class) // graphql-ws
        val GQL_ERROR = OperationType("error", List::class) // shared
        val GQL_CONNECTION_KEEP_ALIVE = OperationType("ka", Nothing::class) // apollo-only, graphql-ws uses PING/PONG

        // bidirectional
        val GQL_PING = OperationType("ping", Map::class) // graphql-ws-only, apollo uses CONNECTION_KEEP_ALIVE
        val GQL_PONG = OperationType("pong", Map::class) // graphql-ws-only, apollo uses CONNECTION_KEEP_ALIVE
        val GQL_COMPLETE = OperationType("complete", Nothing::class) // shared, but server->client only in apollo

        /**
         * Factory method for converting the string type to an [OperationType] instance
         */
        @JvmStatic
        @JsonCreator
        fun getOperationType(type: String): OperationType<*>? {
            return Companion::class.memberProperties
                .filterIsInstance<KProperty1<Companion, OperationType<*>>>()
                .map { it.get(Companion) }
                .find { it.type == type }
        }
    }
}
