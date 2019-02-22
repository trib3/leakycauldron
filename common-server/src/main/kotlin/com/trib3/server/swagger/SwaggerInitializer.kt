package com.trib3.server.swagger

import com.trib3.server.config.TribeApplicationConfig
import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder
import io.swagger.v3.jaxrs2.integration.OpenApiServlet
import io.swagger.v3.oas.integration.SwaggerConfiguration
import io.swagger.v3.oas.integration.api.OpenApiContext
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.servers.Server
import javax.inject.Inject
import javax.ws.rs.core.Application

// JaxrsOpenApiContextBuilder<T> needs to be subclassed in order to instantiate with a <T>
private class SwaggerContextBuilder : JaxrsOpenApiContextBuilder<SwaggerContextBuilder>()

// TODO: find a better place for this interface
interface JaxrsAppProcessor {
    fun process(application: Application)
}

class SwaggerInitializer
@Inject constructor(val appConfig: TribeApplicationConfig) : JaxrsAppProcessor {
    override fun process(application: Application) {
        // this is tricky, but we want to document the jersey exposed APIs
        // from within the admin servlet, and the swagger libraries don't
        // make things easy.  Fake out the servlet context -> swagger context
        // with what ServletConfigContextUtils.getContextIdFromServletConfig
        // will generate for the actual servlet, then add the swagger servlet
        val ctxId = OpenApiContext.OPENAPI_CONTEXT_ID_PREFIX + "servlet." + OpenApiServlet::class.simpleName
        SwaggerContextBuilder()
            .openApiConfiguration(
                SwaggerConfiguration().openAPI(
                    OpenAPI().servers(listOf(Server().url("http://${appConfig.corsDomain}:${appConfig.appPort}/app")))
                )
            )
            .application(application)
            .ctxId(ctxId)
            .buildContext(true)
    }
}
