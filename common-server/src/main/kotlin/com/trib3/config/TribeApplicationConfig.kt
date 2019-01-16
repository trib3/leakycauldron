package com.trib3.config

import io.github.config4k.extract

/**
 * Application config object that exposes basic things about the main service
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
}