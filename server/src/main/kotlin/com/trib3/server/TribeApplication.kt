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
import com.trib3.server.modules.EnvironmentCallback
import com.trib3.server.modules.ServletConfig
import com.trib3.server.modules.ServletFilterConfig
import com.trib3.server.modules.TribeApplicationModule
import com.trib3.server.swagger.JaxrsAppProcessor
import dev.misfitlabs.kotlinguice4.getInstance
import io.dropwizard.auth.AuthDynamicFeature
import io.dropwizard.auth.AuthFilter
import io.dropwizard.configuration.ConfigurationFactoryFactory
import io.dropwizard.core.Application
import io.dropwizard.core.Configuration
import io.dropwizard.core.ConfiguredBundle
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import io.dropwizard.jetty.setup.ServletEnvironment
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.prometheus.metrics.instrumentation.dropwizard.DropwizardExports
import io.prometheus.metrics.instrumentation.dropwizard5.labels.CustomLabelMapper
import io.prometheus.metrics.instrumentation.dropwizard5.labels.MapperConfig
import jakarta.inject.Inject
import jakarta.inject.Named
import org.eclipse.jetty.server.Handler
import javax.annotation.Nullable

private val log =
    run {
        KotlinLoggingConfiguration.logStartupMessage = false
        KotlinLogging.logger { }
    }

/**
 * A dropwizard Application that allows Guice configuration of the application
 */
class TribeApplication
    @Inject
    @Suppress("LongParameterList")
    constructor(
        val appConfig: TribeApplicationConfig,
        val objectMapper: ObjectMapper,
        val metricRegistry: MetricRegistry,
        val healthCheckRegistry: HealthCheckRegistry,
        val configurationFactoryFactory: ConfigurationFactoryFactory<Configuration>,
        val dropwizardBundles: Set<ConfiguredBundle<Configuration>>,
        val servletFilterConfigs: Set<ServletFilterConfig>,
        @Named(TribeApplicationModule.ADMIN_SERVLET_FILTERS_BIND_NAME)
        val adminServletFilterConfigs: Set<ServletFilterConfig>,
        val healthChecks: Set<HealthCheck>,
        val jaxrsAppProcessors: Set<JaxrsAppProcessor>,
        @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
        val jerseyResources: Set<Any>,
        @Named(TribeApplicationModule.APPLICATION_SERVLETS_BIND_NAME)
        val appServlets: Set<ServletConfig>,
        @Named(TribeApplicationModule.ADMIN_SERVLETS_BIND_NAME)
        val adminServlets: Set<ServletConfig>,
        @Nullable val authFilter: AuthFilter<*, *>?,
        val envCallbacks: Set<EnvironmentCallback>,
        val rootHandler: Handler.Singleton,
    ) : Application<Configuration>() {
        val versionHealthCheck: VersionHealthCheck =
            healthChecks.first {
                it is VersionHealthCheck
            } as VersionHealthCheck

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
        override fun getName(): String = appConfig.appName

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
        private fun addServlet(
            servletEnv: ServletEnvironment,
            servletConfig: ServletConfig,
        ) {
            val servlet = servletEnv.addServlet(servletConfig.name, servletConfig.servlet)
            servletConfig.mappings.forEach { mapping -> servlet.addMapping(mapping) }
        }

        fun addServletFilters(
            target: ServletEnvironment,
            configs: Set<ServletFilterConfig>,
        ) {
            configs.forEach {
                val filter = target.addFilter(it.name, it.filterClass)
                filter.initParameters = it.initParameters
                filter.addMappingForUrlPatterns(it.dispatcherTypes, it.isMatchAfter, *it.urlPatterns.toTypedArray())
            }
        }

        /**
         * Runs the application
         */
        override fun run(
            conf: Configuration,
            env: Environment,
        ) {
            env.applicationContext.insertHandler(rootHandler)
            jerseyResources.forEach { env.jersey().register(it) }
            authFilter?.let { env.jersey().register(AuthDynamicFeature(it)) }

            jaxrsAppProcessors.forEach { it.process(env.jersey().resourceConfig) }

            addServletFilters(env.servlets(), servletFilterConfigs)
            addServletFilters(env.admin(), adminServletFilterConfigs)

            appServlets.forEach { addServlet(env.servlets(), it) }
            adminServlets.forEach { addServlet(env.admin(), it) }

            healthChecks.forEach { env.healthChecks().register(it::class.simpleName, it) }
            envCallbacks.forEach { it.invoke(env) }
            DropwizardExports
                .builder()
                .dropwizardRegistry(metricRegistry)
                .customLabelMapper(
                    object : CustomLabelMapper(
                        listOf(
                            MapperConfig(
                                // This config won't actually match what we want because the * glob stops at '.'s
                                // but we do need a non-empty list of MapperConfigs to use a CustomLabelMapper
                                "*.total",
                                $$"${0}.total",
                                mapOf(),
                            ),
                        ),
                    ) {
                        override fun getName(dropwizardName: String?): String {
                            // this one actually remaps **.total -> **.async
                            return super.getName(dropwizardName?.replace("\\.total$".toRegex(), ".async"))
                        }
                    },
                ).register()
            log.info {
                "Initializing service ${appConfig.appName} in environment ${appConfig.env}" +
                    " with version info: ${versionHealthCheck.info()} "
            }
        }
    }

/**
 * Main entry point.  Always calls the 'server' command.
 */
fun main() {
    TribeApplication.INSTANCE.run("server")
}
