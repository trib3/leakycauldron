package com.trib3.graphql.resources

import com.codahale.metrics.annotation.Timed
import com.trib3.graphql.execution.GraphQLRequest
import graphql.ExecutionInput
import graphql.GraphQL
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.websocket.server.WebSocketServerFactory
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import javax.inject.Inject
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * Jersey Resource entry point to GraphQL execution.  Configures the graphql schemas at
 * injection time and then executes a [GraphRequest] specified query when requested.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
open class GraphQLResource
@Inject constructor(
    private val graphQL: GraphQL,
    creator: WebSocketCreator
) {
    private val webSocketFactory = WebSocketServerFactory().apply {
        this.creator = creator
        this.start()
    }

    /**
     * Execute the query specified by the [GraphRequest]
     */
    @POST
    @Path("/graphql")
    @Timed
    open fun graphQL(query: GraphQLRequest): Response {
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query(query.query)
                .variables(query.variables ?: mapOf())
                .operationName(query.operationName)
                .build()
        )
        return Response.ok(result).build()
    }

    /**
     * For websocket subscriptions, support upgrading from GET to a websocket
     */
    @GET
    @Path("/graphql")
    @Timed
    open fun graphQLUpgrade(@Context request: HttpServletRequest, @Context response: HttpServletResponse): Response {
        if (webSocketFactory.isUpgradeRequest(request, response)) {
            if (webSocketFactory.acceptWebSocket(request, response)) {
                return Response.status(HttpStatus.SWITCHING_PROTOCOLS_101).build()
            }
        }
        return Response.status(HttpStatus.METHOD_NOT_ALLOWED_405).build()
    }
}
