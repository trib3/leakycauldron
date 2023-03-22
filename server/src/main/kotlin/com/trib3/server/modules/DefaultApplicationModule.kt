package com.trib3.server.modules

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.google.inject.Scopes
import com.google.inject.multibindings.ProvidesIntoSet
import com.palominolabs.metrics.guice.MetricsInstrumentationModule
import com.trib3.config.modules.KMSModule
import com.trib3.json.modules.ObjectMapperModule
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.coroutine.CoroutineModelProcessor
import com.trib3.server.filters.RequestIdFilter
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.healthchecks.VersionHealthCheck
import com.trib3.server.resources.AuthCookieResource
import com.trib3.server.resources.PingResource
import dev.misfitlabs.kotlinguice4.multibindings.KotlinMultibinder
import io.dropwizard.Configuration
import io.dropwizard.auth.AuthValueFactoryProvider
import io.dropwizard.configuration.ConfigurationFactoryFactory
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.eclipse.jetty.servlets.HeaderFilter
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature
import java.security.Principal
import javax.inject.Provider
import javax.servlet.Filter

data class ServletFilterConfig(
    val name: String,
    val filterClass: Class<out Filter>,
    val initParameters: Map<String, String> = emptyMap(),
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
        // Bind common health checks
        val healthChecks = KotlinMultibinder.newSetBinder<HealthCheck>(kotlinBinder)
        healthChecks.addBinding().to<PingHealthCheck>()
        healthChecks.addBinding().to<VersionHealthCheck>()

        val filterBinder = KotlinMultibinder.newSetBinder<ServletFilterConfig>(kotlinBinder)
        filterBinder.addBinding().toInstance(
            ServletFilterConfig(RequestIdFilter::class.java.simpleName, RequestIdFilter::class.java),
        )

        // Bind standard resources resources
        resourceBinder().addBinding().to<PingResource>()
        resourceBinder().addBinding().to<AuthCookieResource>()

        // Bind coroutine model processor
        resourceBinder().addBinding().toInstance(CoroutineModelProcessor::class.java)

        // set up metrics for guice created instances
        val registry = MetricRegistry()
        bind<MetricRegistry>().toInstance(registry)
        install(MetricsInstrumentationModule.builder().withMetricRegistry(registry).build())
        bind<HealthCheckRegistry>().`in`(Scopes.SINGLETON)

        // Ensure @Auth annotations can be used as long as downstream binds an AuthFilter implementation
        // (and optionally an Authorizer implementation)
        resourceBinder().addBinding().toInstance(RolesAllowedDynamicFeature::class.java)
        resourceBinder().addBinding().toInstance(AuthValueFactoryProvider.Binder(Principal::class.java))
        authFilterBinder().setDefault().toProvider(Provider { null })
        authorizerBinder().setDefault().toProvider(Provider { null })
    }

    /**
     * Configure CORS headers to allow the service to be hit from pages hosted
     * by the admin port, the app port, or standard HTTP ports on the configured
     * [TribeApplicationConfig.corsDomain]
     */
    @ProvidesIntoSet
    fun provideCorsFilter(appConfig: TribeApplicationConfig): ServletFilterConfig {
        val corsDomain =
            appConfig.corsDomains.map {
                "https?://*.?$it," +
                    "https?://*.?$it:${appConfig.appPort}"
            }.joinToString(",")
        val paramMap = mapOf(
            CrossOriginFilter.ALLOWED_ORIGINS_PARAM to corsDomain,
            CrossOriginFilter.ALLOWED_METHODS_PARAM to "GET,POST,PUT,DELETE,OPTIONS,PATCH,HEAD",
            CrossOriginFilter.ALLOW_CREDENTIALS_PARAM to "true",
        )
        return ServletFilterConfig(
            CrossOriginFilter::class.java.simpleName,
            CrossOriginFilter::class.java,
            paramMap,
        )
    }

    /**
     * Enables creation of a Jersey Servlet HeaderFilter that configures HTTPS headers to turn on specified
     * security settings, such as HSTS, content-options, frame-options, XSS options and Content Security Policy.
     * It returns a ServletFilterConfig object that specifies the HeaderFilter and the updated HTTPS header values.
     * Header values are supplied via the application.httpHeaders parameter in [TribeApplicationConfig.httpsHeaders].
     * The value of application.httpHeaders can be an empty list.
     *
     * @param appConfig the application configuration setting, including an httpHeaders value
     * @return a ServletFilterConfig object for configuring the Jersey Servlet HeaderFilter.
     */

    @ProvidesIntoSet
    fun provideHeaderFilter(appConfig: TribeApplicationConfig): ServletFilterConfig {
        val headerConfig = appConfig.httpsHeaders.map { "Set $it" }.joinToString(",")
        val paramMap = mapOf(
            "headerConfig" to headerConfig,
        )
        return ServletFilterConfig(
            HeaderFilter::class.java.simpleName,
            HeaderFilter::class.java,
            paramMap,
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
