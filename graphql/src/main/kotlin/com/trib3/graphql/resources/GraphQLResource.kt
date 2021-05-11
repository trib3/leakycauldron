package com.trib3.graphql.resources

import com.codahale.metrics.annotation.Timed
import com.expediagroup.graphql.generator.execution.GraphQLContext
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.GraphQLRequest
import com.trib3.graphql.execution.toExecutionInput
import com.trib3.graphql.modules.DataLoaderRegistryFactory
import com.trib3.graphql.websocket.GraphQLContextWebSocketCreatorFactory
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.coroutine.AsyncDispatcher
import com.trib3.server.filters.RequestIdFilter
import com.trib3.server.runIf
import graphql.GraphQL
import io.dropwizard.auth.Auth
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Parameter
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
import javax.ws.rs.container.ContainerRequestContext
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
    @Nullable val dataLoaderRegistryFactory: DataLoaderRegistryFactory? = null,
    appConfig: TribeApplicationConfig
) {
    // Using the CrossOriginFilter directly is tricky because it avoids setting
    // CORS headers on websocket requests, so mimic its regex and evaluate that
    // when doing CORS checking of websockets
    private val corsRegex = Regex(
        appConfig.corsDomains.joinToString("|") {
            "https?://*.?$it|" +
                "https?://*.?$it:${appConfig.appPort}"
        }.replace(".", "\\.").replace("*", ".*")
    )

    internal val runningFutures = ConcurrentHashMap<String, CoroutineScope>()

    @get:Hidden
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
     * Returns a response telling the browser that basic authorization is required
     */
    private fun unauthorizedResponse(): Response {
        return Response.status(HttpStatus.UNAUTHORIZED_401).header(
            "WWW-Authenticate", "Basic realm=\"realm\""
        ).build()
    }

    /**
     * Execute the query specified by the [GraphRequest]
     */
    @POST
    @Path("/graphql")
    @Timed
    @AsyncDispatcher("IO")
    open suspend fun graphQL(
        @Parameter(hidden = true) @Auth principal: Optional<Principal>,
        query: GraphQLRequest
    ): Response = supervisorScope {
        val context = GraphQLResourceContext(principal.orElse(null), this)
        val requestId = RequestIdFilter.getRequestId()
        if (requestId != null) {
            runningFutures[requestId] = this
        }
        try {
            val futureResult = graphQL.executeAsync(
                query.toExecutionInput(context, dataLoaderRegistryFactory)
            ).whenComplete { result, throwable ->
                log.debug("$requestId finished with $result, $throwable")
            }
            val result = futureResult.await()
            if (result.getData<Any?>() == null && result.errors.all { it.message == "HTTP 401 Unauthorized" }) {
                unauthorizedResponse()
            } else {
                Response.ok(result)
                    // Communicate any set cookie back to the client
                    .runIf(context.cookie != null) {
                        cookie(context.cookie)
                    }.build()
            }
        } finally {
            if (requestId != null) {
                runningFutures.remove(requestId)
            }
        }
    }

    /**
     * Allow cancellation of running queries by [requestId].
     */
    @DELETE
    @Path("/graphql")
    fun cancel(
        @Parameter(hidden = true) @Auth principal: Optional<Principal>,
        @QueryParam("id") requestId: String
    ): Response {
        if (graphQLConfig.checkAuthorization && !principal.isPresent) {
            return unauthorizedResponse()
        }
        val running = runningFutures[requestId]
        if (running != null) {
            running.coroutineContext[Job]?.cancelChildren()
        }
        return Response.status(HttpStatus.NO_CONTENT_204).build()
    }

    /**
     * For websocket subscriptions, support upgrading from GET to a websocket
     */
    @GET
    @Path("/graphql")
    @Timed
    open fun graphQLUpgrade(
        @Parameter(hidden = true) @Auth principal: Optional<Principal>,
        @Context request: HttpServletRequest,
        @Context response: HttpServletResponse,
        @Context containerRequestContext: ContainerRequestContext
    ): Response {
        val origin = request.getHeader("Origin")
        if (origin != null) {
            if (!origin.matches(corsRegex)) {
                return unauthorizedResponse()
            }
        }
        // Create a new WebSocketCreator for each request bound to an optional authorized principal
        val creator = creatorFactory.getCreator(containerRequestContext)
        return if (webSocketFactory.isUpgradeRequest(request, response) &&
            webSocketFactory.acceptWebSocket(creator, request, response)
        ) {
            Response.status(HttpStatus.SWITCHING_PROTOCOLS_101).build()
        } else {
            Response.status(HttpStatus.METHOD_NOT_ALLOWED_405).build()
        }
    }
}
