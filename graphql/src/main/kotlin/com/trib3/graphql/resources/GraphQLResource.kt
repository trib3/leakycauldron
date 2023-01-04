package com.trib3.graphql.resources

import com.codahale.metrics.annotation.Timed
import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.types.GraphQLBatchResponse
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLResponse
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.modules.KotlinDataLoaderRegistryFactoryProvider
import com.trib3.graphql.websocket.GraphQLContextWebSocketCreatorFactory
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.coroutine.AsyncDispatcher
import com.trib3.server.filters.RequestIdFilter
import graphql.GraphQL
import graphql.GraphQLContext
import io.dropwizard.auth.Auth
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Parameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
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
import javax.ws.rs.core.Response
import kotlin.collections.set

private val log = KotlinLogging.logger {}

/**
 * Helper method for null-safe context map construction
 */
inline fun <reified T> contextMap(value: T?): Map<*, Any> {
    return value?.let { mapOf(T::class to value) }.orEmpty()
}

/**
 * Helper method to construct a [GraphQLContext]'s underlying [Map] from a [CoroutineScope] and optional [Principal].
 */
fun getGraphQLContextMap(
    scope: CoroutineScope,
    principal: Principal? = null,
): Map<*, Any> {
    return contextMap(scope) + contextMap(principal)
}

/**
 * Returns a response telling the browser that basic authorization is required
 */
internal fun unauthorizedResponse(): Response {
    return Response.status(HttpStatus.UNAUTHORIZED_401).header(
        "WWW-Authenticate",
        "Basic realm=\"realm\"",
    ).build()
}

/**
 * Jersey Resource entry point to GraphQL execution.  Configures the graphql schemas at
 * injection time and then executes a [GraphQLRequest] specified query when requested.
 */
@Path("/graphql")
@Produces(MediaType.APPLICATION_JSON)
open class GraphQLResource
@Inject constructor(
    private val graphQL: GraphQL,
    private val graphQLConfig: GraphQLConfig,
    private val creatorFactory: GraphQLContextWebSocketCreatorFactory,
    @Nullable val dataLoaderRegistryFactoryProvider: KotlinDataLoaderRegistryFactoryProvider? = null,
    appConfig: TribeApplicationConfig,
) {
    // Using the CrossOriginFilter directly is tricky because it avoids setting
    // CORS headers on websocket requests, so mimic its regex and evaluate that
    // when doing CORS checking of websockets
    private val corsRegex = Regex(
        appConfig.corsDomains.joinToString("|") {
            "https?://*.?$it|" +
                "https?://*.?$it:${appConfig.appPort}"
        }.replace(".", "\\.").replace("*", ".*"),
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
     * Execute the query specified by the [GraphQLRequest]
     */
    @POST
    @Timed
    @AsyncDispatcher("IO")
    open suspend fun graphQL(
        @Parameter(hidden = true) @Auth
        principal: Optional<Principal>,
        query: GraphQLServerRequest,
        @Context requestContext: ContainerRequestContext? = null,
    ): Response = supervisorScope {
        val responseBuilder = Response.ok()
        val contextMap =
            getGraphQLContextMap(this, principal.orElse(null)) +
                contextMap(responseBuilder) +
                contextMap(requestContext)

        val requestId = RequestIdFilter.getRequestId()
        if (requestId != null) {
            runningFutures[requestId] = this
        }
        try {
            val factory = dataLoaderRegistryFactoryProvider?.invoke(query, contextMap)
            val response = GraphQLRequestHandler(graphQL, factory).executeRequest(query, graphQLContext = contextMap)
            log.debug("$requestId finished with $response")
            val responses = when (response) {
                is GraphQLResponse<*> -> listOf(response)
                is GraphQLBatchResponse -> response.responses
            }
            if (
                responses.all { r -> r.data == null } &&
                responses.all { r ->
                    val errors = r.errors
                    errors != null && errors.all { it.message == "HTTP 401 Unauthorized" }
                }
            ) {
                unauthorizedResponse()
            } else {
                responseBuilder.entity(response).build()
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
    fun cancel(
        @Parameter(hidden = true) @Auth
        principal: Optional<Principal>,
        @QueryParam("id") requestId: String,
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
    @Timed
    open fun graphQLUpgrade(
        @Parameter(hidden = true) @Auth
        principal: Optional<Principal>,
        @Context request: HttpServletRequest,
        @Context response: HttpServletResponse,
        @Context containerRequestContext: ContainerRequestContext,
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
