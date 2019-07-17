package com.trib3.server.graphql

import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.server.filters.RequestIdFilter
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import mu.KotlinLogging
import org.eclipse.jetty.websocket.api.StatusCode
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.UUID

private val log = KotlinLogging.logger { }

/**
 * A [WebSocketAdapter] that accepts a GraphQL query and executes it.  Returns a GraphQL ExecutionResult
 * for regular operations, but if a subscription operation returns a [Publisher], will stream an
 * ExecutionResult over the websocket for each published result.
 *
 * Implements https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */
class GraphQLWebSocket(
    val graphQL: GraphQL?,
    val objectMapper: ObjectMapper
) : WebSocketAdapter() {
    val objectWriter = objectMapper.writerWithDefaultPrettyPrinter()
    override fun onWebSocketText(message: String) {
        check(graphQL != null) {
            "graphQL not configured!"
        }
        RequestIdFilter.withRequestId {
            val result = graphQL.execute(
                ExecutionInput.newExecutionInput()
                    .query(message)
                    .build()
            )
            val publisherData = try {
                result.getData<Publisher<ExecutionResult>>()
            } catch (e: Exception) {
                null
            }
            if (publisherData == null) {
                remote.sendString(objectWriter.writeValueAsString(result))
                if (result.errors.isEmpty()) {
                    session.close(StatusCode.NORMAL, "End of results")
                } else {
                    session.close(StatusCode.SERVER_ERROR, "Error in results")
                }
            } else {
                val loggingRequestId = RequestIdFilter.getRequestId() ?: UUID.randomUUID().toString()
                publisherData.subscribe(object : Subscriber<ExecutionResult> {
                    lateinit var subscription: Subscription
                    override fun onComplete() {
                        session.close(StatusCode.NORMAL, "End of results")
                        RequestIdFilter.withRequestId(loggingRequestId) {
                            log.info("End of results")
                        }
                    }

                    override fun onSubscribe(s: Subscription) {
                        s.request(1)
                        this.subscription = s
                    }

                    override fun onNext(result: ExecutionResult) {
                        RequestIdFilter.withRequestId(loggingRequestId) {
                            subscription.request(1)
                            val instrumentedResult = RequestIdInstrumentation()
                                .instrumentExecutionResult(result, null)
                            remote.sendString(
                                objectWriter.writeValueAsString(
                                    instrumentedResult.get()
                                )
                            )
                        }
                    }

                    override fun onError(cause: Throwable) {
                        session.close(StatusCode.SERVER_ERROR, cause.message)
                        RequestIdFilter.withRequestId(loggingRequestId) {
                            log.error("Error in result stream: ${cause.message}", cause)
                        }
                    }
                })
            }
        }
    }

    override fun onWebSocketError(cause: Throwable) {
        session.close(StatusCode.SERVER_ERROR, cause.message)
        log.error("Error in websocket: ${cause.message}", cause)
    }
}
