package com.trib3.graphql.resources

import com.codahale.metrics.annotation.Timed
import com.expediagroup.graphql.execution.GraphQLContext
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.GraphQLRequest
import com.trib3.graphql.execution.toExecutionInput
import com.trib3.graphql.modules.DataLoaderRegistryFactory
import com.trib3.graphql.websocket.GraphQLContextWebSocketCreatorFactory
import com.trib3.server.coroutine.AsyncDispatcher
import com.trib3.server.filters.RequestIdFilter
import graphql.GraphQL
import io.dropwizard.auth.Auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.future.await
import kotlinx.coroutines.supervisorScope
import mu.KotlinLogging
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.websocket.server.WebSocketServerFactory
import java.security.Principal
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.Nullable
import javax.inject.Inject
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.NewCookie
import javax.ws.rs.core.Response

private val log = KotlinLogging.logger {}

/**
 * Context class for communicating auth status to/from GraphQL queries/mutations/subscriptions.
 * A request's authorized [Principal] may be read from this [GraphQLContext].  If a GraphQL
 * operation (eg, a login/logout mutation) needs to set a cookie, it can set a [cookie] on
 * the context object and that cookie will be sent to the client (note this only works via
 * a POST request, not via a query executing over a websocket).
 */
data class GraphQLResourceContext(
    val principal: Principal?,
    private val scope: CoroutineScope = GlobalScope,
    var cookie: NewCookie? = null
) : GraphQLContext, CoroutineScope by scope

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
    private val creatorFactory: GraphQLContextWebSocketCreatorFactory,
    @Nullable val dataLoaderRegistryFactory: DataLoaderRegistryFactory? = null
) {

    internal val runningFutures = ConcurrentHashMap<String, CoroutineScope>()

    internal val webSocketFactory = WebSocketServerFactory().apply {
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
    @AsyncDispatcher("IO")
    open suspend fun graphQL(@Auth principal: Optional<Principal>, query: GraphQLRequest): Response = supervisorScope {
        val context = GraphQLResourceContext(principal.orElse(null), this)
        val requestId = RequestIdFilter.getRequestId()
        val futureResult = graphQL.executeAsync(
            query.toExecutionInput(context, dataLoaderRegistryFactory)
        ).whenComplete { result, throwable ->
            if (requestId != null) {
                log.debug("$requestId finished with $result, $throwable")
                runningFutures.remove(requestId)
            }
        }
        if (requestId != null) {
            runningFutures[requestId] = this
        }

        val result = futureResult.await()
        if (result.getData<Any?>() == null && result.errors.all { it.message == "HTTP 401 Unauthorized" }) {
            Response.status(HttpStatus.UNAUTHORIZED_401).header(
                "WWW-Authenticate", "Basic realm=\"Realm\""
            ).build()
        } else {
            Response.ok(result)
                .let {
                    // Communicate any set cookie back to the client
                    if (context.cookie != null) {
                        it.cookie(context.cookie)
                    } else {
                        it
                    }
                }.build()
        }
    }

    /**
     * Allow cancellation of running queries by [requestId].
     */
    @DELETE
    @Path("/graphql")
    fun cancel(@QueryParam("id") requestId: String) {
        val running = runningFutures[requestId]
        if (running != null) {
            running.coroutineContext[Job]?.cancelChildren()
        }
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
        if (graphQLConfig.authorizedWebSocketOnly && !principal.isPresent) {
            return Response.status(HttpStatus.UNAUTHORIZED_401).header(
                "WWW-Authenticate", "Basic realm=\"Realm\""
            ).build()
        }
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
