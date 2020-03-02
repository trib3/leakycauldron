package com.trib3.graphql.modules

import com.expediagroup.graphql.SchemaGeneratorConfig
import com.expediagroup.graphql.TopLevelObject
import com.expediagroup.graphql.execution.FlowSubscriptionExecutionStrategy
import com.expediagroup.graphql.execution.SimpleKotlinDataFetcherFactoryProvider
import com.expediagroup.graphql.toSchema
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Provides
import com.trib3.graphql.execution.CustomDataFetcherExceptionHandler
import com.trib3.graphql.execution.DateTimeHooks
import com.trib3.graphql.execution.RequestIdInstrumentation
import com.trib3.graphql.resources.GraphQLResource
import com.trib3.graphql.websocket.GraphQLWebSocketCreator
import com.trib3.server.modules.ServletConfig
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import io.dropwizard.servlets.assets.AssetServlet
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import javax.inject.Named

/**
 * Default Guice module for GraphQL applications.  Sets up
 * the GraphQL resource and GraphiQL assets, and provides
 * a GraphQL instance given configured bindings.
 */
class DefaultGraphQLModule : GraphQLApplicationModule() {
    override fun configureApplication() {
        bind<WebSocketCreator>().to<GraphQLWebSocketCreator>()
        resourceBinder().addBinding().to<GraphQLResource>()
        // Ensure graphql binders are set up
        graphQLPackagesBinder()
        graphQLQueriesBinder()
        graphQLMutationsBinder()
        graphQLSubscriptionsBinder()

        adminServletBinder().addBinding().toInstance(
            ServletConfig(
                "GraphiQLAssetServlet",
                AssetServlet(
                    "graphiql",
                    "/graphiql",
                    "index.html",
                    Charsets.UTF_8
                ),
                listOf("/graphiql")
            )
        )
    }

    @Provides
    fun provideGraphQLInstance(
        @Named(GRAPHQL_PACKAGES_BIND_NAME)
        graphQLPackages: Set<@JvmSuppressWildcards String>,
        @Named(GRAPHQL_QUERIES_BIND_NAME)
        queries: Set<@JvmSuppressWildcards Any>,
        @Named(GRAPHQL_MUTATIONS_BIND_NAME)
        mutations: Set<@JvmSuppressWildcards Any>,
        @Named(GRAPHQL_SUBSCRIPTIONS_BIND_NAME)
        subscriptions: Set<@JvmSuppressWildcards Any>,
        mapper: ObjectMapper
    ): GraphQL {
        val hooks = DateTimeHooks()
        val config = SchemaGeneratorConfig(
            graphQLPackages.toList(),
            hooks = hooks,
            dataFetcherFactoryProvider = SimpleKotlinDataFetcherFactoryProvider(mapper)
        )
        return GraphQL.newGraphQL(
            toSchema(
                config,
                queries.toList().map { TopLevelObject(it) },
                mutations.toList().map { TopLevelObject(it) },
                subscriptions.toList().map { TopLevelObject(it) }
            )
        )
            .queryExecutionStrategy(AsyncExecutionStrategy(CustomDataFetcherExceptionHandler()))
            .subscriptionExecutionStrategy(FlowSubscriptionExecutionStrategy(CustomDataFetcherExceptionHandler()))
            .instrumentation(RequestIdInstrumentation())
            .build()
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean {
        return other is DefaultGraphQLModule
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}
