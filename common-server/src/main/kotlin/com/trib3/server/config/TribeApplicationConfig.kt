package com.trib3.server.config

import com.trib3.config.ConfigLoader
import com.trib3.config.extract
import com.typesafe.config.Config
import javax.inject.Inject

/**
 * Application config object that exposes basic things about the main service
 */
class TribeApplicationConfig
@Inject constructor(loader: ConfigLoader) {
    val env: String
    val appName: String
    val corsDomain: String
    val appPort: Int
    val adminPort: Int

    init {
        val config = loader.load()
        env = config.extract("env")
        appName = config.extract("application.name")
        corsDomain = config.extract("application.domain")
        appPort = config.extract<List<Config>>("server.applicationConnectors").first().getInt("port")
        adminPort = config.extract<List<Config>>("server.adminConnectors").first().getInt("port")
    }
}
