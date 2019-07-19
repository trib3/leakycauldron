package com.trib3.server.graphql

import com.fasterxml.jackson.annotation.JsonCreator
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
    val payload: T? = null
)

/**
 * Captures the mapping from operation type to payload type for the messages in the protocol
 */
data class OperationType<T : Any>(
    @get:JsonValue val type: String,
    val payloadType: KClass<T>
) {
    companion object {
        // client -> server
        val GQL_CONNECTION_INIT = OperationType("connection_init", Nothing::class)
        val GQL_START = OperationType("start", GraphRequest::class)
        val GQL_STOP = OperationType("stop", Nothing::class)
        val GQL_CONNECTION_TERMINATE = OperationType("connection_terminate", Nothing::class)
        // server -> client
        val GQL_CONNECTION_ERROR = OperationType("error", Nothing::class)
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
