package com.trib3.server.modules

/**
 * Default module for running serverless.  Binds jersey AWS dependencies.
 */
class ServerlessApplicationModule: TribeApplicationModule() {
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
    }
}