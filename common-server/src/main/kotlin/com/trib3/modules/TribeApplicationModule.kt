package com.trib3.modules

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.google.inject.name.Names

/**
 * Base class for modules that bind things for TribeApplication.
 * Provides binder methods for commonly bound members of the TribeApplication.
 */
abstract class TribeApplicationModule: AbstractModule() {

    companion object {
        const val APPLICATION_RESOURCES_BIND_NAME = "ApplicationResources"
    }

    /**
     * Binder for jersey resources
     */
    fun resourceBinder(): Multibinder<Any> {
        return Multibinder.newSetBinder(
            binder(),
            Any::class.java,
            Names.named(APPLICATION_RESOURCES_BIND_NAME)
        )
    }
}