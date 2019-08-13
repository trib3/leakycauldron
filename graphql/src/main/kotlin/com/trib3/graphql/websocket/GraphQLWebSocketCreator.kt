package com.trib3.graphql.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.graphql.GraphQLConfig
import graphql.GraphQL
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import javax.annotation.Nullable
import javax.inject.Inject

/**
 * [WebSocketCreator] that creates a [GraphQLWebSocketAdapter], a [Flowable] that bridges the
 * WebSocket API into rx events, and a [GraphQLWebSocketSubscriber] to subscribe to those events.
 */
class GraphQLWebSocketCreator
@Inject constructor(
    @Nullable val graphQL: GraphQL?,
    val objectMapper: ObjectMapper,
    val graphQLConfig: GraphQLConfig
) : WebSocketCreator {

    /**
     * Create the [GraphQLWebSocketAdapter] and its [Flowable], and subscribe the [subscriber] on the specified
     * [scheduler] ([Schedulers.io] by default)
     */
    fun createWebSocket(
        subscriber: GraphQLWebSocketSubscriber,
        scheduler: Scheduler = Schedulers.io()
    ): GraphQLWebSocketAdapter {
        lateinit var adapter: GraphQLWebSocketAdapter
        Flowable.create<OperationMessage<*>>({ emitter ->
            adapter = GraphQLWebSocketAdapter(emitter, objectMapper)
        }, BackpressureStrategy.BUFFER)
            .observeOn(scheduler)
            .subscribe(subscriber)
        subscriber.adapter = adapter
        subscriber.afterSubscribed()
        return adapter
    }

    /**
     * Create the [GraphQLWebSocketAdapter] and set the graphql-ws subprotocol in our upgrade response
     */
    override fun createWebSocket(req: ServletUpgradeRequest, resp: ServletUpgradeResponse): Any {
        resp.acceptedSubProtocol = graphQLConfig.webSocketSubProtocol
        return createWebSocket(GraphQLWebSocketSubscriber(graphQL, graphQLConfig))
    }
}
