package com.trib3.graphql.resources

import com.codahale.metrics.annotation.Timed
import com.expediagroup.graphql.dataloader.KotlinDataLoaderRegistryFactory
import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.types.GraphQLBatchResponse
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLResponse
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import com.trib3.graphql.GraphQLConfig
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.coroutine.AsyncDispatcher
import com.trib3.server.filters.RequestIdFilter
import graphql.GraphQL
import graphql.GraphQLContext
import io.dropwizard.auth.Auth
import io.swagger.v3.oas.annotations.Parameter
import jakarta.inject.Inject
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.supervisorScope
import mu.KotlinLogging
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.util.URIUtil
import org.eclipse.jetty.websocket.core.Configuration
import org.eclipse.jetty.websocket.core.server.WebSocketCreator
import org.eclipse.jetty.websocket.core.server.WebSocketMappings
import org.eclipse.jetty.websocket.server.internal.JettyServerFrameHandlerFactory
import java.security.Principal
import java.time.Duration
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.Nullable
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
    @Nullable val dataLoaderRegistryFactory: KotlinDataLoaderRegistryFactory? = null,
    appConfig: TribeApplicationConfig,
    private val creator: WebSocketCreator,
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

    private val webSocketConfig = Configuration.ConfigurationCustomizer().apply {
        if (graphQLConfig.idleTimeout != null) {
            this.idleTimeout = Duration.ofMillis(graphQLConfig.idleTimeout)
        }
        if (graphQLConfig.maxBinaryMessageSize != null) {
            this.maxBinaryMessageSize = graphQLConfig.maxBinaryMessageSize
        }
        if (graphQLConfig.maxTextMessageSize != null) {
            this.maxTextMessageSize = graphQLConfig.maxTextMessageSize
        }
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
            val response = GraphQLRequestHandler(
                graphQL,
                dataLoaderRegistryFactory,
            ).executeRequest(query, graphQLContext = GraphQLContext.of(contextMap))
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
        val webSocketMapping = request.servletContext?.let {
            WebSocketMappings.getMappings(it)
        }
        val pathSpec = WebSocketMappings.parsePathSpec(URIUtil.addPaths(request.servletPath, request.pathInfo))
        if (webSocketMapping != null && webSocketMapping.getWebSocketCreator(pathSpec) == null) {
            webSocketMapping.addMapping(
                pathSpec,
                creator,
                JettyServerFrameHandlerFactory.getFactory(request.servletContext),
                webSocketConfig,
            )
        }

        // Create a new WebSocketCreator for each request bound to an optional authorized principal
        return if (webSocketMapping != null && webSocketMapping.upgrade(request, response, null)) {
            Response.status(HttpStatus.SWITCHING_PROTOCOLS_101).build()
        } else {
            Response.status(HttpStatus.METHOD_NOT_ALLOWED_405).build()
        }
    }
}
