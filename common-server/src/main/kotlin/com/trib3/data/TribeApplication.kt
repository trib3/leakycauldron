package com.trib3.data

import com.codahale.metrics.health.HealthCheck
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.multibindings.Multibinder
import com.google.inject.name.Names
import com.trib3.data.healthchecks.PingHealthCheck
import com.trib3.data.healthchecks.VersionHealthCheck
import com.typesafe.config.ConfigFactory
import io.dropwizard.Application
import io.dropwizard.Configuration
import io.dropwizard.setup.Environment
import io.github.config4k.extract
import mu.KotlinLogging
import javax.inject.Inject
import javax.inject.Named

val log = KotlinLogging.logger { }

/**
 * A dropwizard Application that allows Guice configuration of resources and health checks
 */
class TribeApplication @Inject constructor(
    @Named(APPLICATION_NAME_BIND_NAME) private val appName: String,
    @Named(APPLICATION_RESOURCES_BIND_NAME) private val jerseyResources: Set<@JvmSuppressWildcards Any>,
    private val healthChecks: Set<@JvmSuppressWildcards HealthCheck>,
    private val versionHealthCheck: VersionHealthCheck
) : Application<Configuration>() {

    var env: Environment? = null

    companion object {
        const val APPLICATION_NAME_BIND_NAME = "ApplicationName"
        const val APPLICATION_RESOURCES_BIND_NAME = "ApplicationResources"
    }

    override fun getName(): String {
        return appName
    }

    override fun run(conf: Configuration, env: Environment) {
        this.env = env
        for (resource in jerseyResources) {
            env.jersey().register(resource)
        }
        for (healthCheck in healthChecks) {
            env.healthChecks().register(healthCheck::class.simpleName, healthCheck)
        }
        log.info("Initializing service with version info: {} ", versionHealthCheck.info())
    }
}

/**
 * The default module, binds our common health check implementations
 */
class ApplicationModule(val appName: String) : AbstractModule() {
    override fun configure() {
        bind(String::class.java).annotatedWith(
            Names.named(TribeApplication.APPLICATION_NAME_BIND_NAME)
        ).toInstance(appName)

        val healthChecks = Multibinder.newSetBinder(binder(), HealthCheck::class.java)
        healthChecks.addBinding().to(PingHealthCheck::class.java)
        healthChecks.addBinding().to(VersionHealthCheck::class.java)
    }
}

fun init(): TribeApplication {

    val config = ConfigFactory.load()
    val builtinModules = listOf(ApplicationModule(config.extract<String>("service.name")))
    val appModules = config.extract<List<String>>("service.modules").map {
        Class.forName(it).getDeclaredConstructor().newInstance() as AbstractModule
    }
    val injector = Guice.createInjector(listOf(builtinModules, appModules).flatten())
    val app = injector.getInstance(TribeApplication::class.java)
    app.run("server")
    return app
}

fun main(args: Array<String>) {
    init()
}