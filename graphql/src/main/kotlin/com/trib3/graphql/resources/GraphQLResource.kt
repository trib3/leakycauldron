package com.trib3.graphql.resources

import com.codahale.metrics.annotation.Timed
import com.expediagroup.graphql.execution.GraphQLContext
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.GraphQLRequest
import com.trib3.graphql.websocket.GraphQLContextWebSocketCreatorFactory
import graphql.ExecutionInput
import graphql.GraphQL
import io.dropwizard.auth.Auth
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.websocket.server.WebSocketServerFactory
import java.security.Principal
import java.util.Optional
import javax.inject.Inject
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.NewCookie
import javax.ws.rs.core.Response

/**
 * Context class for communicating auth status to/from GraphQL queries/mutations/subscriptions.
 * A request's authorized [Principal] may be read from this [GraphQLContext].  If a GraphQL
 * operation (eg, a login/logout mutation) needs to set a cookie, it can set a [cookie] on
 * the context object and that cookie will be sent to the client (note this only works via
 * a POST request, not via a query executing over a websocket).
 */
data class GraphQLResourceContext(
    val principal: Principal?,
    var cookie: NewCookie? = null
) : GraphQLContext

/**
 * Jersey Resource entry point to GraphQL execution.  Configures the graphql schemas at
 * injection time and then executes a [GraphRequest] specified query when requested.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
open class GraphQLResource
@Inject constructor(
    private val graphQL: GraphQL,
    private val graphQLConfig: GraphQLConfig,
    private val creatorFactory: GraphQLContextWebSocketCreatorFactory
) {
    internal val webSocketFactory = WebSocketServerFactory().apply {
        if (graphQLConfig.asyncWriteTimeout != null) {
            this.policy.asyncWriteTimeout = graphQLConfig.asyncWriteTimeout
        }
        if (graphQLConfig.idleTimeout != null) {
            this.policy.idleTimeout = graphQLConfig.idleTimeout
        }
        if (graphQLConfig.maxBinaryMessageSize != null) {
            this.policy.maxBinaryMessageSize = graphQLConfig.maxBinaryMessageSize
        }
        if (graphQLConfig.maxTextMessageSize != null) {
            this.policy.maxTextMessageSize = graphQLConfig.maxTextMessageSize
        }
        this.start()
    }

    /**
     * Execute the query specified by the [GraphRequest]
     */
    @POST
    @Path("/graphql")
    @Timed
    open fun graphQL(@Auth principal: Optional<Principal>, query: GraphQLRequest): Response {
        val context = GraphQLResourceContext(principal.orElse(null))
        val result = graphQL.execute(
            ExecutionInput.newExecutionInput()
                .query(query.query)
                .variables(query.variables ?: mapOf())
                .operationName(query.operationName)
                .context(context)
                .build()
        )
        val builder = Response.ok(result)
        // Communicate any set cookie back to the client
        return if (context.cookie != null) {
            builder.cookie(context.cookie)
        } else {
            builder
        }.build()
    }

    /**
     * For websocket subscriptions, support upgrading from GET to a websocket
     */
    @GET
    @Path("/graphql")
    @Timed
    open fun graphQLUpgrade(
        @Auth principal: Optional<Principal>,
        @Context request: HttpServletRequest,
        @Context response: HttpServletResponse
    ): Response {
        // Create a new WebSocketCreator for each request bound to an optional authorized principal
        val creator = creatorFactory.getCreator(GraphQLResourceContext(principal.orElse(null)))
        if (webSocketFactory.isUpgradeRequest(request, response)) {
            if (webSocketFactory.acceptWebSocket(creator, request, response)) {
                return Response.status(HttpStatus.SWITCHING_PROTOCOLS_101).build()
            }
        }
        return Response.status(HttpStatus.METHOD_NOT_ALLOWED_405).build()
    }
}
