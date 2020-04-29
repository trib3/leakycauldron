package com.trib3.graphql.modules

import com.google.inject.name.Names
import com.trib3.graphql.execution.GraphQLRequest
import com.trib3.server.modules.TribeApplicationModule
import dev.misfitlabs.kotlinguice4.multibindings.KotlinMultibinder
import dev.misfitlabs.kotlinguice4.multibindings.KotlinOptionalBinder
import graphql.execution.instrumentation.Instrumentation
import org.dataloader.DataLoaderRegistry

/**
 * Function that takes a [GraphQLRequest] and an optional context
 * and returns a [DataLoaderRegistry] with registered [org.dataloader.DataLoader]s
 *
 * Invoked per GraphQL request to allow for batching of data fetchers
 */
typealias DataLoaderRegistryFactory = Function2<
    @JvmSuppressWildcards GraphQLRequest,
    @JvmSuppressWildcards Any?,
    @JvmSuppressWildcards DataLoaderRegistry
    >

/**
 * Base class for GraphQL application guice modules.  Provides
 * binders for GraphQL packages, queries, mutations, and subscriptions,
 * and ensures that a [DefaultGraphQLModule] is installed.
 */
abstract class GraphQLApplicationModule : TribeApplicationModule() {

    companion object {
        const val GRAPHQL_PACKAGES_BIND_NAME = "graphQLPackages"
        const val GRAPHQL_QUERIES_BIND_NAME = "graphQLQueries"
        const val GRAPHQL_MUTATIONS_BIND_NAME = "graphQLMutations"
        const val GRAPHQL_SUBSCRIPTIONS_BIND_NAME = "graphQLSubscriptions"
    }

    /**
     * Configure application bindings here
     */
    abstract fun configureApplication()

    final override fun configure() {
        super.configure()
        install(DefaultGraphQLModule())
        configureApplication()
    }

    /**
     * Binder for graphql packages
     */
    fun graphQLPackagesBinder(): KotlinMultibinder<String> {
        return KotlinMultibinder.newAnnotatedSetBinder(
            kotlinBinder,
            Names.named(GRAPHQL_PACKAGES_BIND_NAME)
        )
    }

    /**
     * Binder for graphql queries
     */
    fun graphQLQueriesBinder(): KotlinMultibinder<Any> {
        return KotlinMultibinder.newAnnotatedSetBinder(
            kotlinBinder,
            Names.named(GRAPHQL_QUERIES_BIND_NAME)
        )
    }

    /**
     * Binder for graphql mutations
     */
    fun graphQLMutationsBinder(): KotlinMultibinder<Any> {
        return KotlinMultibinder.newAnnotatedSetBinder(
            kotlinBinder,
            Names.named(GRAPHQL_MUTATIONS_BIND_NAME)
        )
    }

    /**
     * Binder for graphql subscriptions
     */
    fun graphQLSubscriptionsBinder(): KotlinMultibinder<Any> {
        return KotlinMultibinder.newAnnotatedSetBinder(
            kotlinBinder,
            Names.named(GRAPHQL_SUBSCRIPTIONS_BIND_NAME)
        )
    }

    /**
     * Optional binder for the dataLoaderRegistryFactory
     */
    fun dataLoaderRegistryFactoryBinder(): KotlinOptionalBinder<DataLoaderRegistryFactory> {
        return KotlinOptionalBinder.newOptionalBinder(kotlinBinder)
    }

    /**
     * Binder for graphql Instrumentations.  RequestIdInstrumentation
     * will be registered by default, but additional ones can be registered
     * via this binder.
     */
    fun graphQLInstrumentationsBinder(): KotlinMultibinder<Instrumentation> {
        return KotlinMultibinder.newSetBinder(kotlinBinder)
    }
}
