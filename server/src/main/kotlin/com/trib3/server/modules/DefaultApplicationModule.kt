package com.trib3.server.modules

import com.authzee.kotlinguice4.multibindings.KotlinMultibinder
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.expedia.graphql.SchemaGeneratorConfig
import com.expedia.graphql.TopLevelObject
import com.expedia.graphql.toSchema
import com.google.inject.Provides
import com.google.inject.Scopes
import com.google.inject.multibindings.ProvidesIntoSet
import com.palominolabs.metrics.guice.MetricsInstrumentationModule
import com.trib3.config.modules.KMSModule
import com.trib3.json.modules.ObjectMapperModule
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.filters.RequestIdFilter
import com.trib3.server.graphql.CustomDataFetcherExceptionHandler
import com.trib3.server.graphql.DateTimeHooks
import com.trib3.server.graphql.GraphQLWebSocketCreator
import com.trib3.server.graphql.RequestIdInstrumentation
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.healthchecks.VersionHealthCheck
import com.trib3.server.resources.GraphQLResource
import com.trib3.server.resources.PingResource
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.SubscriptionExecutionStrategy
import io.dropwizard.Configuration
import io.dropwizard.configuration.ConfigurationFactoryFactory
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import javax.inject.Named
import javax.servlet.Filter

data class ServletFilterConfig(
    val name: String,
    val filterClass: Class<out Filter>,
    val initParameters: Map<String, String> = emptyMap()
)

/**
 * The default guice module, binds things common to dropwizard and serverless execution
 */
class DefaultApplicationModule : TribeApplicationModule() {
    override fun configure() {
        install(KMSModule())
        install(ObjectMapperModule())
        // bind HOCON configuration parser
        bind<ConfigurationFactoryFactory<Configuration>>().to<HoconConfigurationFactoryFactory<Configuration>>()
        // Bind common health checks
        val healthChecks = KotlinMultibinder.newSetBinder<HealthCheck>(kotlinBinder)
        healthChecks.addBinding().to<PingHealthCheck>()
        healthChecks.addBinding().to<VersionHealthCheck>()

        val filterBinder = KotlinMultibinder.newSetBinder<ServletFilterConfig>(kotlinBinder)
        filterBinder.addBinding().toInstance(
            ServletFilterConfig(RequestIdFilter::class.java.simpleName, RequestIdFilter::class.java)
        )

        // Bind ping and graphql resources
        resourceBinder().addBinding().to<PingResource>()
        resourceBinder().addBinding().to<GraphQLResource>()
        bind<WebSocketCreator>().to<GraphQLWebSocketCreator>()
        // Ensure graphql binders are set up
        graphQLPackagesBinder()
        graphQLQueriesBinder()
        graphQLMutationsBinder()
        graphQLSubscriptionsBinder()

        // set up metrics for guice created instances
        val registry = MetricRegistry()
        bind<MetricRegistry>().toInstance(registry)
        install(MetricsInstrumentationModule.builder().withMetricRegistry(registry).build())
        bind<HealthCheckRegistry>().`in`(Scopes.SINGLETON)
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
        subscriptions: Set<@JvmSuppressWildcards Any>
    ): GraphQL? {
        val config = SchemaGeneratorConfig(graphQLPackages.toList(), hooks = DateTimeHooks())
        return if (queries.isNotEmpty()) {
            GraphQL.newGraphQL(
                toSchema(
                    config,
                    queries.toList().map { TopLevelObject(it) },
                    mutations.toList().map { TopLevelObject(it) },
                    subscriptions.toList().map { TopLevelObject(it) }
                )
            )
                .queryExecutionStrategy(AsyncExecutionStrategy(CustomDataFetcherExceptionHandler()))
                .subscriptionExecutionStrategy(SubscriptionExecutionStrategy(CustomDataFetcherExceptionHandler()))
                .instrumentation(RequestIdInstrumentation())
                .build()
        } else {
            null
        }
    }

    /**
     * Configure CORS headers to allow the service to be hit from pages hosted
     * by the admin port, the app port, or standard HTTP ports on the configured
     * [TribeApplicationConfig.corsDomain]
     */
    @ProvidesIntoSet
    fun provideCorsFilter(appConfig: TribeApplicationConfig): ServletFilterConfig {
        val corsDomain =
            appConfig.corsDomains.map {
                "https?://*.?$it," +
                        "https?://*.?$it:${appConfig.appPort}"
            }.joinToString(",")
        val paramMap = mapOf(
            CrossOriginFilter.ALLOWED_ORIGINS_PARAM to corsDomain,
            CrossOriginFilter.ALLOWED_METHODS_PARAM to "GET,POST,PUT,DELETE,OPTIONS,PATCH,HEAD",
            CrossOriginFilter.ALLOW_CREDENTIALS_PARAM to "true"
        )
        return ServletFilterConfig(
            CrossOriginFilter::class.java.simpleName,
            CrossOriginFilter::class.java,
            paramMap
        )
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean {
        return other is DefaultApplicationModule
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}
