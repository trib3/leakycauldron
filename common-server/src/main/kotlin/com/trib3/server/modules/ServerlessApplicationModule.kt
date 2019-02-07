package com.trib3.server.modules

import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.config.modules.KMSModule
import com.trib3.server.resources.AdminResource
import io.dropwizard.jersey.jackson.JacksonBinder
import io.dropwizard.setup.ExceptionMapperBinder

/**
 * Default module for running serverless.  Binds jersey AWS dependencies and
 * jersey resources that the dropwizard server registers automatically.
 */
class ServerlessApplicationModule : TribeApplicationModule() {
    override fun configure() {
//        resourceBinder().addBinding().toInstance(object : AbstractBinder() {
//            override fun configure() {
//                bindFactory(AwsProxyServletContextFactory::class.java)!!
//                    .to(ServletContext::class.java).`in`(RequestScoped::class.java)
//                bindFactory(AwsProxyServletRequestFactory::class.java)!!
//                    .to(HttpServletRequest::class.java).`in`(RequestScoped::class.java)
//                bindFactory(AwsProxyServletResponseFactory::class.java)!!
//                    .to(HttpServletResponse::class.java).`in`(RequestScoped::class.java)
//            }
//        })
        val resourceBinder = resourceBinder()
        resourceBinder.addBinding().toConstructor(
            JacksonBinder::class.java.getConstructor(ObjectMapper::class.java)
        )
        resourceBinder.addBinding().toInstance(ExceptionMapperBinder(false))
        resourceBinder.addBinding().to(AdminResource::class.java)
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
