package com.trib3.server.modules

import com.authzee.kotlinguice4.multibindings.KotlinMultibinder
import com.google.inject.multibindings.ProvidesIntoSet
import com.trib3.config.modules.KMSModule
import com.trib3.json.modules.ObjectMapperModule
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.logging.RequestIdFilter
import io.dropwizard.Configuration
import io.dropwizard.configuration.ConfigurationFactoryFactory
import org.eclipse.jetty.servlets.CrossOriginFilter
import javax.servlet.Filter

data class ServletFilterConfig(
    val name: String,
    val filterClass: Class<out Filter>,
    val initParameters: Map<String, String> = emptyMap()
)

/**
 * The default guice module, binds things common to dropwizard and serverless execution
 */
class DefaultApplicationModule : TribeApplicationModule() {
    override fun configure() {
        install(KMSModule())
        install(ObjectMapperModule())
        // bind HOCON configuration parser
        bind<ConfigurationFactoryFactory<Configuration>>().to<HoconConfigurationFactoryFactory<Configuration>>()

        val filterBinder = KotlinMultibinder.newSetBinder<ServletFilterConfig>(kotlinBinder)
        filterBinder.addBinding().toInstance(
            ServletFilterConfig(RequestIdFilter::class.java.simpleName, RequestIdFilter::class.java)
        )

        // Make sure the resource binder is set up
        resourceBinder()
    }

    /**
     * Configure CORS headers to allow the service to be hit from pages hosted
     * by the admin port, the app port, or standard HTTP ports on the configured
     * [TribeApplicationConfig.corsDomain]
     */
    @ProvidesIntoSet
    fun provideCorsFilter(appConfig: TribeApplicationConfig): ServletFilterConfig {
        val corsDomain =
            "https?://*.?${appConfig.corsDomain}," +
                "https?://*.?${appConfig.corsDomain}:${appConfig.appPort}," +
                "https?://*.?${appConfig.corsDomain}:${appConfig.adminPort}"
        val paramMap = mapOf(
            CrossOriginFilter.ALLOWED_ORIGINS_PARAM to corsDomain,
            CrossOriginFilter.ALLOWED_METHODS_PARAM to "GET,POST,PUT,DELETE,OPTIONS,PATCH,HEAD",
            CrossOriginFilter.ALLOW_CREDENTIALS_PARAM to "true"
        )
        return ServletFilterConfig(
            CrossOriginFilter::class.java.simpleName,
            CrossOriginFilter::class.java,
            paramMap
        )
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean {
        return other is DefaultApplicationModule
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}
