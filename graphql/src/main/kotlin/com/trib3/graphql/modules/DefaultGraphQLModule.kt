package com.trib3.graphql.modules

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.execution.FlowSubscriptionExecutionStrategy
import com.expediagroup.graphql.generator.toSchema
import com.google.inject.Provides
import com.trib3.graphql.execution.CustomDataFetcherExceptionHandler
import com.trib3.graphql.execution.LeakyCauldronHooks
import com.trib3.graphql.execution.RequestIdInstrumentation
import com.trib3.graphql.resources.GraphQLResource
import com.trib3.graphql.resources.GraphQLSseResource
import com.trib3.graphql.websocket.GraphQLContextWebSocketCreatorFactory
import com.trib3.graphql.websocket.GraphQLWebSocketCreatorFactory
import com.trib3.graphql.websocket.GraphQLWebSocketDropwizardAuthenticator
import com.trib3.server.modules.ServletConfig
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import io.dropwizard.servlets.assets.AssetServlet
import javax.inject.Named
import javax.inject.Provider

/**
 * Default Guice module for GraphQL applications.  Sets up
 * the GraphQL resource and GraphiQL assets, and provides
 * a GraphQL instance given configured bindings.
 */
class DefaultGraphQLModule : GraphQLApplicationModule() {
    override fun configureApplication() {
        bind<GraphQLContextWebSocketCreatorFactory>().to<GraphQLWebSocketCreatorFactory>()
        // by default, null DataLoaderRegistryFactoryProvider is configured, applications can
        // override this by setting a binding
        dataLoaderRegistryFactoryProviderBinder()
            .setDefault().toProvider(Provider { null })
        // by default, any AuthFilter that is registered to guice will be used for
        // authenticating websocket connections during the websocket upgrade or
        // the [OperationType.GQL_CONNNECTION_INIT] message.  Applications can provide
        // their own GraphQLAuthenticator by setting a binding
        graphQLWebSocketAuthenticatorBinder()
            .setDefault()
            .to<GraphQLWebSocketDropwizardAuthenticator>()

        dataFetcherExceptionHandlerBinder().setDefault().to<CustomDataFetcherExceptionHandler>()

        resourceBinder().addBinding().to<GraphQLResource>()
        resourceBinder().addBinding().to<GraphQLSseResource>()

        // Ensure graphql binders are set up
        graphQLPackagesBinder()
        graphQLQueriesBinder()
        graphQLMutationsBinder()
        graphQLSubscriptionsBinder()
        graphQLInstrumentationsBinder()
        schemaDirectivesBinder()

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
        graphQLPackages: Set<String>,
        @Named(GRAPHQL_QUERIES_BIND_NAME)
        queries: Set<Any>,
        @Named(GRAPHQL_MUTATIONS_BIND_NAME)
        mutations: Set<Any>,
        @Named(GRAPHQL_SUBSCRIPTIONS_BIND_NAME)
        subscriptions: Set<Any>,
        instrumentations: Set<Instrumentation>,
        hooks: LeakyCauldronHooks = LeakyCauldronHooks(),
        exceptionHandler: DataFetcherExceptionHandler = CustomDataFetcherExceptionHandler()
    ): GraphQL {
        val config = SchemaGeneratorConfig(
            graphQLPackages.toList(),
            hooks = hooks
        )
        return GraphQL.newGraphQL(
            toSchema(
                config,
                queries.toList().map { TopLevelObject(it) },
                mutations.toList().map { TopLevelObject(it) },
                subscriptions.toList().map { TopLevelObject(it) }
            )
        )
            .queryExecutionStrategy(AsyncExecutionStrategy(exceptionHandler))
            .mutationExecutionStrategy(AsyncExecutionStrategy(exceptionHandler))
            .subscriptionExecutionStrategy(FlowSubscriptionExecutionStrategy(exceptionHandler))
            .instrumentation(ChainedInstrumentation(listOf(RequestIdInstrumentation()) + instrumentations.toList()))
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
