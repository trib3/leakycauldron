package com.trib3.server.resources

import com.codahale.metrics.annotation.Timed
import com.expedia.graphql.SchemaGeneratorConfig
import com.expedia.graphql.TopLevelObject
import com.expedia.graphql.toSchema
import com.fasterxml.jackson.annotation.JsonIgnore
import com.trib3.server.modules.TribeApplicationModule.Companion.GRAPHQL_MUTATIONS_BIND_NAME
import com.trib3.server.modules.TribeApplicationModule.Companion.GRAPHQL_PACKAGES_BIND_NAME
import com.trib3.server.modules.TribeApplicationModule.Companion.GRAPHQL_QUERIES_BIND_NAME
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQLError
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.ExecutionPath
import graphql.language.SourceLocation
import mu.KotlinLogging
import javax.inject.Inject
import javax.inject.Named
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

private val log = KotlinLogging.logger { }

/**
 * Custom Exception Handler implantation allowing control over sanitation, relevance and specificity
 */
class CustomDataFetcherExceptionHandler : DataFetcherExceptionHandler {

    override fun onException(
        handlerParameters: DataFetcherExceptionHandlerParameters
    ): DataFetcherExceptionHandlerResult {
        val exception = handlerParameters.exception
        val sourceLocation = handlerParameters.sourceLocation
        val path = handlerParameters.path
        val error: GraphQLError = SanitizedGraphQLError(path, exception, sourceLocation)
        log.warn(error.message, exception)
        return DataFetcherExceptionHandlerResult.newResult().error(error).build()
    }
}

/**
 * Removes exception from the error JSON serialization keeping it out of the API response
 * and attempts to bubble up the message from the root cause of the exception
 */
class SanitizedGraphQLError(
    path: ExecutionPath,
    exception: Throwable,
    sourceLocation: SourceLocation
) : ExceptionWhileDataFetching(path, exception, sourceLocation) {

    override fun getMessage(): String {
        return getCause(super.getException()).message ?: super.getMessage()
    }

    @JsonIgnore
    override fun getException(): Throwable {
        return super.getException()
    }

    /**
     * Find the root cause of the exception being thrown
     * (Root cause will either have a null or itself under it)
     */
    private fun getCause(e: Throwable): Throwable {
        var result: Throwable = e

        while (result.cause != null && result != result.cause) {
            result = result.cause!!
        }
        return result
    }
}

/**
 * A generic GraphQL Request object that includes query, variable, and operationName components
 */
data class GraphRequest(val query: String, val variables: Map<String, Any>?, val operationName: String?)

/**
 * Jersey Resource entry point to GraphQL execution.  Configures the graphql schemas at
 * injection time and then executes a [GraphRequest] specified query when requested.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
open class GraphqlResource
@Inject constructor(
    @Named(GRAPHQL_PACKAGES_BIND_NAME)
    graphQLPackages: Set<@JvmSuppressWildcards String>,
    @Named(GRAPHQL_QUERIES_BIND_NAME)
    queries: Set<@JvmSuppressWildcards Any>,
    @Named(GRAPHQL_MUTATIONS_BIND_NAME)
    mutations: Set<@JvmSuppressWildcards Any>
) {
    private val graphql: GraphQL?

    init {
        val config =
            SchemaGeneratorConfig(graphQLPackages.toList())
        graphql = if (queries.isNotEmpty()) {
            GraphQL.newGraphQL(
                toSchema(
                    config,
                    queries.toList().map { TopLevelObject(it) },
                    mutations.toList().map { TopLevelObject(it) }
                )
            )
                .queryExecutionStrategy(AsyncExecutionStrategy(CustomDataFetcherExceptionHandler()))
                .build()
        } else {
            null
        }
    }

    /**
     * Execute the query specified by the [GraphRequest]
     */
    @POST
    @Path("/graphql")
    @Timed
    open fun graphQl(query: GraphRequest): Response {
        check(graphql != null) {
            "Graphql not configured!"
        }
        val result = graphql.execute(
            ExecutionInput.newExecutionInput()
                .query(query.query)
                .variables(query.variables ?: mapOf())
                .operationName(query.operationName)
                .build()
        )
        return Response.ok(result).build()
    }
}
