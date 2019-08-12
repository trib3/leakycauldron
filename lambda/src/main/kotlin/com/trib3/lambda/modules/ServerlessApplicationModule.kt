package com.trib3.lambda.modules

import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.config.modules.KMSModule
import com.trib3.lambda.resources.AdminResource
import com.trib3.server.modules.TribeApplicationModule
import io.dropwizard.jersey.jackson.JacksonFeature
import io.dropwizard.setup.ExceptionMapperBinder

/**
 * Default module for running serverless.  Binds jersey AWS dependencies and
 * jersey resources that the dropwizard server registers automatically.
 */
class ServerlessApplicationModule : TribeApplicationModule() {
    override fun configure() {
        val resourceBinder = resourceBinder()
        resourceBinder.addBinding().toConstructor(
            JacksonFeature::class.java.getConstructor(ObjectMapper::class.java)
        )
        resourceBinder.addBinding().toInstance(ExceptionMapperBinder(false))
        resourceBinder.addBinding().to<AdminResource>()
        install(KMSModule())
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean {
        return other is ServerlessApplicationModule
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}
