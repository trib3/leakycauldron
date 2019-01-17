package com.trib3

import com.codahale.metrics.health.HealthCheck
import com.trib3.config.TribeApplicationConfig
import com.trib3.healthchecks.VersionHealthCheck
import com.trib3.modules.DefaultApplicationModule
import com.trib3.modules.DropwizardApplicationModule
import com.trib3.modules.TribeApplicationModule
import io.dropwizard.Application
import io.dropwizard.Bundle
import io.dropwizard.Configuration
import io.dropwizard.configuration.ConfigurationFactoryFactory
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import mu.KotlinLogging
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Named
import javax.servlet.DispatcherType
import javax.servlet.Filter


private val log = KotlinLogging.logger { }

/**
 * A dropwizard Application that allows Guice configuration of the application
 */
class TribeApplication @Inject constructor(
    val appConfig: TribeApplicationConfig,
    val configurationFactoryFactory: ConfigurationFactoryFactory<@JvmSuppressWildcards Configuration>,
    val dropwizardBundles: Set<@JvmSuppressWildcards Bundle>,
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME) val jerseyResources: Set<@JvmSuppressWildcards Any>,
    val servletFilters: Set<@JvmSuppressWildcards Class<out Filter>>,
    val healthChecks: Set<@JvmSuppressWildcards HealthCheck>
) : Application<Configuration>() {
    val versionHealthCheck: VersionHealthCheck = healthChecks.first { it is VersionHealthCheck } as VersionHealthCheck

    /*
     * statically initializes a TribeApplication via guice using the modules specified in the application.conf
     */
    companion object {
        val INSTANCE: TribeApplication

        init {
            val config = TribeApplicationConfig()
            val injector = config.getInjector(listOf(DefaultApplicationModule(), DropwizardApplicationModule()))
            INSTANCE = injector.getInstance(TribeApplication::class.java)
        }
    }

    /**
     * returns the application name
     */
    override fun getName(): String {
        return appConfig.serviceName
    }

    /**
     * Bootstraps the application
     */
    override fun initialize(bootstrap: Bootstrap<Configuration>) {
        bootstrap.configurationFactoryFactory = configurationFactoryFactory
        dropwizardBundles.forEach(bootstrap::addBundle)
    }

    /**
     * Runs the application
     */
    override fun run(conf: Configuration, env: Environment) {
        jerseyResources.forEach { env.jersey().register(it) }
        servletFilters.forEach {
            env.servlets().addFilter(it.simpleName, it)
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*")
        }
        healthChecks.forEach { env.healthChecks().register(it::class.simpleName, it) }
        log.info(
            "Initializing service {} in environment {} with version info: {} ",
            appConfig.serviceName,
            appConfig.env,
            versionHealthCheck.info()
        )
    }
}

/**
 * Main entry point.  Always calls the 'server' command.
 */
fun main(args: Array<String>) {
    TribeApplication.INSTANCE.run("server")
}