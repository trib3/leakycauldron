package com.trib3.server.resources

import com.codahale.metrics.annotation.Timed
import com.expedia.graphql.SchemaGeneratorConfig
import com.expedia.graphql.TopLevelObject
import com.expedia.graphql.toSchema
import com.trib3.server.graphql.CustomDataFetcherExceptionHandler
import com.trib3.server.graphql.DateTimeHooks
import com.trib3.server.graphql.GraphRequest
import com.trib3.server.modules.TribeApplicationModule.Companion.GRAPHQL_MUTATIONS_BIND_NAME
import com.trib3.server.modules.TribeApplicationModule.Companion.GRAPHQL_PACKAGES_BIND_NAME
import com.trib3.server.modules.TribeApplicationModule.Companion.GRAPHQL_QUERIES_BIND_NAME
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
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
            SchemaGeneratorConfig(graphQLPackages.toList(), hooks = DateTimeHooks())
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
