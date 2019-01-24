package com.trib3.server.modules

import com.fasterxml.jackson.databind.ObjectMapper
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
        resourceBinder().addBinding().toConstructor(
            JacksonBinder::class.java.getConstructor(ObjectMapper::class.java)
        )
        resourceBinder().addBinding().toInstance(ExceptionMapperBinder(false))
    }
}
