package com.trib3.server

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.server.config.BootstrapConfig
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.healthchecks.VersionHealthCheck
import com.trib3.server.modules.DefaultApplicationModule
import com.trib3.server.modules.DropwizardApplicationModule
import com.trib3.server.modules.ServletConfig
import com.trib3.server.modules.ServletFilterConfig
import com.trib3.server.modules.TribeApplicationModule
import com.trib3.server.swagger.JaxrsAppProcessor
import dev.misfitlabs.kotlinguice4.getInstance
import io.dropwizard.Application
import io.dropwizard.Configuration
import io.dropwizard.ConfiguredBundle
import io.dropwizard.auth.AuthDynamicFeature
import io.dropwizard.auth.AuthFilter
import io.dropwizard.configuration.ConfigurationFactoryFactory
import io.dropwizard.jetty.setup.ServletEnvironment
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import mu.KotlinLogging
import java.util.EnumSet
import javax.annotation.Nullable
import javax.inject.Inject
import javax.inject.Named
import javax.servlet.DispatcherType

private val log = KotlinLogging.logger { }

/**
 * A dropwizard Application that allows Guice configuration of the application
 */
class TribeApplication @Inject constructor(
    val appConfig: TribeApplicationConfig,
    val objectMapper: ObjectMapper,
    val metricRegistry: MetricRegistry,
    val healthCheckRegistry: HealthCheckRegistry,
    val configurationFactoryFactory: ConfigurationFactoryFactory<@JvmSuppressWildcards Configuration>,
    val dropwizardBundles: Set<@JvmSuppressWildcards ConfiguredBundle<Configuration>>,
    val servletFilterConfigs: Set<@JvmSuppressWildcards ServletFilterConfig>,
    @Named(TribeApplicationModule.ADMIN_SERVLET_FILTERS_BIND_NAME)
    val adminServletFilterConfigs: Set<@JvmSuppressWildcards ServletFilterConfig>,
    val healthChecks: Set<@JvmSuppressWildcards HealthCheck>,
    val jaxrsAppProcessors: Set<@JvmSuppressWildcards JaxrsAppProcessor>,

    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    val jerseyResources: Set<@JvmSuppressWildcards Any>,

    @Named(TribeApplicationModule.APPLICATION_SERVLETS_BIND_NAME)
    val appServlets: Set<@JvmSuppressWildcards ServletConfig>,

    @Named(TribeApplicationModule.ADMIN_SERVLETS_BIND_NAME)
    val adminServlets: Set<@JvmSuppressWildcards ServletConfig>,
    @Nullable val authFilter: AuthFilter<*, *>?
) : Application<Configuration>() {
    val versionHealthCheck: VersionHealthCheck = healthChecks.first { it is VersionHealthCheck } as VersionHealthCheck

    /*
     * statically initializes a TribeApplication via guice using the modules specified in the application.conf
     */
    companion object {
        val INSTANCE: TribeApplication

        init {
            val config = BootstrapConfig()
            val injector = config.getInjector(listOf(DefaultApplicationModule(), DropwizardApplicationModule()))
            INSTANCE = injector.getInstance<TribeApplication>()
        }
    }

    /**
     * returns the application name
     */
    override fun getName(): String {
        return appConfig.appName
    }

    /**
     * Bootstraps the application
     */
    override fun initialize(bootstrap: Bootstrap<Configuration>) {
        bootstrap.objectMapper = objectMapper
        bootstrap.metricRegistry = metricRegistry
        bootstrap.healthCheckRegistry = healthCheckRegistry
        bootstrap.configurationFactoryFactory = configurationFactoryFactory
        dropwizardBundles.forEach(bootstrap::addBundle)
    }

    /**
     * Add the servlet and all registered mappings to the given environment
     */
    private fun addServlet(servletEnv: ServletEnvironment, servletConfig: ServletConfig) {
        val servlet = servletEnv.addServlet(servletConfig.name, servletConfig.servlet)
        servletConfig.mappings.forEach { mapping -> servlet.addMapping(mapping) }
    }

    /**
     * Runs the application
     */
    override fun run(conf: Configuration, env: Environment) {
        jerseyResources.forEach { env.jersey().register(it) }
        authFilter?.let { env.jersey().register(AuthDynamicFeature(it)) }

        jaxrsAppProcessors.forEach { it.process(env.jersey().resourceConfig) }

        servletFilterConfigs.forEach {
            val filter = env.servlets().addFilter(it.filterClass.simpleName, it.filterClass)
            filter.initParameters = it.initParameters
            filter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*")
        }

        adminServletFilterConfigs.forEach {
            val filter = env.admin().addFilter(it.filterClass.simpleName, it.filterClass)
            filter.initParameters = it.initParameters
            filter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*")
        }

        appServlets.forEach { addServlet(env.servlets(), it) }
        adminServlets.forEach { addServlet(env.admin(), it) }

        healthChecks.forEach { env.healthChecks().register(it::class.simpleName, it) }
        log.info(
            "Initializing service {} in environment {} with version info: {} ",
            appConfig.appName,
            appConfig.env,
            versionHealthCheck.info()
        )
    }
}

/**
 * Main entry point.  Always calls the 'server' command.
 */
fun main() {
    TribeApplication.INSTANCE.run("server")
}
