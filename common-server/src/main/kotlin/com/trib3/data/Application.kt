package com.trib3.data

import com.codahale.metrics.health.HealthCheck
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.multibindings.Multibinder
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
    @Named("ApplicationResources") private val jerseyResources: Set<@JvmSuppressWildcards Any>,
    private val healthChecks: Set<@JvmSuppressWildcards HealthCheck>,
    private val versionHealthCheck: VersionHealthCheck
) : Application<Configuration>() {
    override fun run(conf: Configuration, env: Environment) {
        for (resource in jerseyResources) {
            env.jersey().register(resource)
        }
        for (healthCheck in healthChecks) {
            env.healthChecks().register(healthCheck::class.simpleName, healthCheck)
        }
        log.info("Initializing service with version info: {} ", versionHealthCheck.info())
    }
}

class ApplicationModule : AbstractModule() {
    override fun configure() {
        val healthChecks = Multibinder.newSetBinder(binder(), HealthCheck::class.java)
        healthChecks.addBinding().to(PingHealthCheck::class.java)
        healthChecks.addBinding().to(VersionHealthCheck::class.java)
    }
}

fun main(args: Array<String>) {
    val config = ConfigFactory.load()
    val builtinModules = listOf(ApplicationModule())
    val appModules = config.extract<List<String>>("service.modules").map {
        Class.forName(it).getDeclaredConstructor().newInstance() as AbstractModule
    }
    val injector = Guice.createInjector(listOf(builtinModules, appModules).flatten())
    injector.getInstance(TribeApplication::class.java).run("server")
}