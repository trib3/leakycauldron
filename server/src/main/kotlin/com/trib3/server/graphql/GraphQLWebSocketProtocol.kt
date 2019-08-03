package com.trib3.server.graphql

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import graphql.ExecutionResult
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Model for a message in the graphql websocket protocol
 *
 * https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
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
        Type(name = "start", value = GraphRequest::class),
        Type(name = "data", value = ExecutionResult::class),
        Type(name = "error", value = String::class),
        Type(name = "connection_init", value = Nothing::class),
        Type(name = "connection_terminate", value = Nothing::class),
        Type(name = "connection_ack", value = Nothing::class),
        Type(name = "connection_error", value = String::class),
        Type(name = "stop", value = Nothing::class),
        Type(name = "complete", value = Nothing::class),
        Type(name = "ka", value = Nothing::class)
    )
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
        val GQL_CONNECTION_INIT = OperationType("connection_init", Nothing::class)
        val GQL_START = OperationType("start", GraphRequest::class)
        val GQL_STOP = OperationType("stop", Nothing::class)
        val GQL_CONNECTION_TERMINATE = OperationType("connection_terminate", Nothing::class)
        // server -> client
        val GQL_CONNECTION_ERROR = OperationType("connection_error", String::class)
        val GQL_CONNECTION_ACK = OperationType("connection_ack", Nothing::class)
        val GQL_DATA = OperationType("data", ExecutionResult::class)
        val GQL_ERROR = OperationType("error", String::class)
        val GQL_COMPLETE = OperationType("complete", Nothing::class)
        val GQL_CONNECTION_KEEP_ALIVE = OperationType("ka", Nothing::class)

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
