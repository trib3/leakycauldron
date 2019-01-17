package com.trib3.config

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import io.github.config4k.extract

/**
 * Application config object that exposes basic things about the main service and
 * provides a way to get a guice injector
 */
class TribeApplicationConfig {
    val env: String
    val serviceName: String
    val serviceModules: List<String>

    init {
        val config = ConfigLoader.load()
        env = config.extract("env")
        serviceName = config.extract("service.name")
        serviceModules = config.extract("service.modules")
    }

    fun getInjector(builtinModules: List<AbstractModule>): Injector {
        val appModules = serviceModules.map {
            Class.forName(it).getDeclaredConstructor().newInstance() as AbstractModule
        }
        return Guice.createInjector(listOf(builtinModules, appModules).flatten())
    }
}