package com.trib3.server.config

import ch.qos.logback.classic.Level
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.trib3.config.ConfigLoader
import com.trib3.config.extract
import io.dropwizard.logging.BootstrapLogging

/**
 * Startup configuration object that allows for configuration of the application guice modules
 * and provides a way to get a guice injector
 */
class BootstrapConfig {

    val appModules: List<String>

    init {
        val config = ConfigLoader().load()
        appModules = config.extract("application.modules")
    }

    fun getInjector(builtinModules: List<AbstractModule>): Injector {
        // since we instantiate things before the Application's constructor gets called, bootstrap the
        // logging so that we don't log things we don't want to during instantiation
        BootstrapLogging.bootstrap(Level.WARN)
        System.setProperty("org.jooq.no-logo", "true")
        val appModules = appModules.map {
            Class.forName(it).getDeclaredConstructor().newInstance() as AbstractModule
        }
        return Guice.createInjector(builtinModules + appModules)
    }
}
