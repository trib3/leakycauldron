package com.trib3.server.modules

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.multibindings.Multibinder
import com.google.inject.name.Names
import javax.servlet.Servlet

data class ServletConfig(
    val name: String,
    val servlet: Servlet,
    val mappings: List<String>
)

/**
 * Base class for modules that bind things for TribeApplication.
 * Provides binder methods for commonly bound members of the TribeApplication.
 */
abstract class TribeApplicationModule : KotlinModule() {

    companion object {
        const val APPLICATION_RESOURCES_BIND_NAME = "ApplicationResources"
        const val APPLICATION_SERVLETS_BIND_NAME = "ApplicationServlets"
        const val ADMIN_SERVLETS_BIND_NAME = "AdminServlets"
        const val ADMIN_SERVLET_FILTERS_BIND_NAME = "AdminFilters"

        const val GRAPHQL_PACKAGES_BIND_NAME = "graphQLPackages"
        const val GRAPHQL_QUERIES_BIND_NAME = "graphQLQueries"
        const val GRAPHQL_MUTATIONS_BIND_NAME = "graphQLMutations"
    }

    /**
     * Binder for jersey resources
     */
    fun resourceBinder(): Multibinder<Any> {
        // Can't use KotlinMultibinder to do a named binding, so just use ::class.java notation
        return Multibinder.newSetBinder(
            binder(),
            Any::class.java,
            Names.named(APPLICATION_RESOURCES_BIND_NAME)
        )
    }

    /**
     * Binder for app servlets
     */
    fun appServletBinder(): Multibinder<ServletConfig> {
        // Can't use KotlinMultibinder to do a named binding, so just use ::class.java notation
        return Multibinder.newSetBinder(
            binder(),
            ServletConfig::class.java,
            Names.named(APPLICATION_SERVLETS_BIND_NAME)
        )
    }

    /**
     * Binder for admin servlets
     */
    fun adminServletBinder(): Multibinder<ServletConfig> {
        // Can't use KotlinMultibinder to do a named binding, so just use ::class.java notation
        return Multibinder.newSetBinder(
            binder(),
            ServletConfig::class.java,
            Names.named(ADMIN_SERVLETS_BIND_NAME)
        )
    }

    /**
     * Binder for graphql packages
     */
    fun graphqlPackagesBinder(): Multibinder<String> {
        return Multibinder.newSetBinder(
            binder(),
            String::class.java,
            Names.named(GRAPHQL_PACKAGES_BIND_NAME)
        )
    }

    /**
     * Binder for graphql packages
     */
    fun graphqlQueriesBinder(): Multibinder<Any> {
        return Multibinder.newSetBinder(
            binder(),
            Any::class.java,
            Names.named(GRAPHQL_QUERIES_BIND_NAME)
        )
    }

    /**
     * Binder for graphql packages
     */
    fun graphqlMutationsBinder(): Multibinder<Any> {
        return Multibinder.newSetBinder(
            binder(),
            Any::class.java,
            Names.named(GRAPHQL_MUTATIONS_BIND_NAME)
        )
    }
}
