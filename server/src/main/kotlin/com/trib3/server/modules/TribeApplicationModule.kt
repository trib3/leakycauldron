package com.trib3.server.modules

import com.authzee.kotlinguice4.KotlinModule
import com.authzee.kotlinguice4.multibindings.KotlinMultibinder
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
    }

    /**
     * Binder for jersey resources
     */
    fun resourceBinder(): KotlinMultibinder<Any> {
        return KotlinMultibinder.newAnnotatedSetBinder(
            kotlinBinder,
            Names.named(APPLICATION_RESOURCES_BIND_NAME)
        )
    }

    /**
     * Binder for app servlets
     */
    fun appServletBinder(): KotlinMultibinder<ServletConfig> {
        return KotlinMultibinder.newAnnotatedSetBinder(
            kotlinBinder,
            Names.named(APPLICATION_SERVLETS_BIND_NAME)
        )
    }

    /**
     * Binder for admin servlets
     */
    fun adminServletBinder(): KotlinMultibinder<ServletConfig> {
        return KotlinMultibinder.newAnnotatedSetBinder(
            kotlinBinder,
            Names.named(ADMIN_SERVLETS_BIND_NAME)
        )
    }
}
