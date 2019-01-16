package com.trib3

import com.codahale.metrics.health.HealthCheck
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.multibindings.Multibinder
import com.trib3.config.TribeApplicationConfig
import com.trib3.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.healthchecks.PingHealthCheck
import com.trib3.healthchecks.VersionHealthCheck
import io.dropwizard.Application
import io.dropwizard.Bundle
import io.dropwizard.Configuration
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import mu.KotlinLogging
import javax.inject.Inject
import javax.inject.Named


val log = KotlinLogging.logger { }

/**
 * The default guice module, binds our common health check implementations
 */
class ApplicationModule() : AbstractModule() {
    override fun configure() {
        val healthChecks = Multibinder.newSetBinder(binder(), HealthCheck::class.java)
        healthChecks.addBinding().to(PingHealthCheck::class.java)
        healthChecks.addBinding().to(VersionHealthCheck::class.java)

        // Set up a Bundle multibinder, but no Bundles to bind by default right now
        Multibinder.newSetBinder(binder(), Bundle::class.java)
    }
}

/**
 * A dropwizard Application that allows Guice configuration of resources and health checks
 */
class TribeApplication @Inject constructor(
    private val appConfig: TribeApplicationConfig,
    private val dropwizardBundles: Set<@JvmSuppressWildcards Bundle>,
    @Named(APPLICATION_RESOURCES_BIND_NAME) private val jerseyResources: Set<@JvmSuppressWildcards Any>,
    private val healthChecks: Set<@JvmSuppressWildcards HealthCheck>
) : Application<Configuration>() {

    // Capture the environment on startup so we can access it later if needed
    var env: Environment? = null
    val versionHealthCheck: VersionHealthCheck

    init {
        versionHealthCheck = healthChecks.first { it is VersionHealthCheck } as VersionHealthCheck
    }

    companion object {
        const val APPLICATION_RESOURCES_BIND_NAME = "ApplicationResources"

        fun init(): TribeApplication {
            val config = TribeApplicationConfig()
            val builtinModules = listOf(ApplicationModule())
            val appModules = config.serviceModules.map {
                Class.forName(it).getDeclaredConstructor().newInstance() as AbstractModule
            }
            val injector = Guice.createInjector(listOf(builtinModules, appModules).flatten())
            val app = injector.getInstance(TribeApplication::class.java)
            app.run("server")
            return app
        }
    }

    override fun getName(): String {
        return appConfig.serviceName
    }

    override fun initialize(bootstrap: Bootstrap<Configuration>) {
        dropwizardBundles.forEach(bootstrap::addBundle)
        bootstrap.configurationFactoryFactory =
                HoconConfigurationFactoryFactory<Configuration>();
    }

    override fun run(conf: Configuration, env: Environment) {
        this.env = env
        jerseyResources.forEach { env.jersey().register(it) }
        healthChecks.forEach { env.healthChecks().register(it::class.simpleName, it) }
        log.info(
            "Initializing service {} in environment {} with version info: {} ",
            appConfig.serviceName,
            appConfig.env,
            versionHealthCheck.info()
        )
    }
}

fun main(args: Array<String>) {
    TribeApplication.init()
}