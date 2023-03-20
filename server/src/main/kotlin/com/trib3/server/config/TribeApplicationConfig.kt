package com.trib3.server.config

import com.trib3.config.ConfigLoader
import com.trib3.config.extract
import javax.inject.Inject

/**
 * Application config object that exposes basic things about the main service
 */
class TribeApplicationConfig
@Inject constructor(loader: ConfigLoader) {
    val env: String
    val appName: String
    val corsDomains: List<String>
    val appPort: Int
    val adminAuthToken: String?
    val httpsHeaders: List<String>

    init {
        val config = loader.load()
        env = config.extract("env")
        appName = config.extract("application.name")
        corsDomains = config.extract("application.domains")
        appPort = config.extract<Int>("server.connector.port")
        adminAuthToken = config.extract("application.adminAuthToken")
        httpsHeaders = config.extract("application.httpsHeaders")
    }
}
