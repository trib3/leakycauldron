package com.trib3.graphql.modules

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.execution.FlowSubscriptionExecutionStrategy
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.types.GraphQLBatchRequest
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.expediagroup.graphql.server.types.GraphQLServerRequest
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.google.inject.Provides
import com.google.inject.util.Providers
import com.trib3.graphql.execution.CustomDataFetcherExceptionHandler
import com.trib3.graphql.execution.LeakyCauldronHooks
import com.trib3.graphql.execution.RequestIdInstrumentation
import com.trib3.graphql.resources.GraphQLResource
import com.trib3.graphql.resources.GraphQLSseResource
import com.trib3.graphql.websocket.GraphQLWebSocketCreator
import com.trib3.graphql.websocket.GraphQLWebSocketDropwizardAuthenticator
import com.trib3.json.modules.ObjectMapperModule
import com.trib3.server.modules.ServletConfig
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import io.dropwizard.servlets.assets.AssetServlet
import jakarta.inject.Named
import org.eclipse.jetty.ee10.websocket.server.config.JettyWebSocketServletContainerInitializer
import org.eclipse.jetty.websocket.core.server.WebSocketCreator

/**
 * Default Guice module for GraphQL applications.  Sets up
 * the GraphQL resource and GraphiQL assets, and provides
 * a GraphQL instance given configured bindings.
 */
class DefaultGraphQLModule : GraphQLApplicationModule() {
    override fun configureApplication() {
        bind<WebSocketCreator>().to<GraphQLWebSocketCreator>()
        // by default, null DataLoaderRegistryFactoryProvider is configured, applications can
        // override this by setting a binding
        dataLoaderRegistryFactoryBinder()
            .setDefault()
            .toProvider(Providers.of(null))
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
                    Charsets.UTF_8,
                ),
                listOf("/graphiql"),
            ),
        )
        environmentCallbackBinder().addBinding().toInstance {
            JettyWebSocketServletContainerInitializer.configure(it.applicationContext, null)
        }
        val mixinBinder =
            ObjectMapperModule.objectMapperMixinBinder { binder() }
        mixinBinder
            .addBinding(GraphQLServerRequest::class)
            .toInstance(GraphqlServerRequestMixin::class)
        mixinBinder
            .addBinding(GraphQLRequest::class)
            .toInstance(DontDeserializeMixin::class)
        mixinBinder
            .addBinding(GraphQLBatchRequest::class)
            .toInstance(DontDeserializeMixin::class)
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
        exceptionHandler: DataFetcherExceptionHandler = CustomDataFetcherExceptionHandler(),
    ): GraphQL {
        val config =
            SchemaGeneratorConfig(
                graphQLPackages.toList(),
                hooks = hooks,
            )
        return GraphQL
            .newGraphQL(
                toSchema(
                    config,
                    queries.toList().map { TopLevelObject(it) },
                    mutations.toList().map { TopLevelObject(it) },
                    subscriptions.toList().map { TopLevelObject(it) },
                ),
            ).queryExecutionStrategy(AsyncExecutionStrategy(exceptionHandler))
            .mutationExecutionStrategy(AsyncExecutionStrategy(exceptionHandler))
            .subscriptionExecutionStrategy(FlowSubscriptionExecutionStrategy(exceptionHandler))
            .instrumentation(ChainedInstrumentation(listOf(RequestIdInstrumentation()) + instrumentations.toList()))
            .build()
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean = other is DefaultGraphQLModule

    override fun hashCode(): Int = this::class.hashCode()
}

// Jackson2 compatibility shims for GraphQLServerRequest
@JsonDeserialize(using = GraphQLServerRequestDeserializer::class)
interface GraphqlServerRequestMixin

class GraphQLServerRequestDeserializer : JsonDeserializer<GraphQLServerRequest>() {
    override fun deserialize(
        parser: JsonParser,
        ctxt: DeserializationContext,
    ): GraphQLServerRequest {
        val codec = parser.codec
        val jsonNode = codec.readTree<JsonNode>(parser)
        return if (jsonNode.isArray) {
            codec.treeToValue(jsonNode, GraphQLBatchRequest::class.java)
        } else {
            codec.treeToValue(jsonNode, GraphQLRequest::class.java)
        }
    }
}

@JsonDeserialize(using = JsonDeserializer.None::class)
interface DontDeserializeMixin
