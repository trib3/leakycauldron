package com.trib3.server.modules

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.multibindings.Multibinder
import com.google.inject.name.Names

/**
 * Base class for modules that bind things for TribeApplication.
 * Provides binder methods for commonly bound members of the TribeApplication.
 */
abstract class TribeApplicationModule : KotlinModule() {

    companion object {
        const val APPLICATION_RESOURCES_BIND_NAME = "ApplicationResources"
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
}
