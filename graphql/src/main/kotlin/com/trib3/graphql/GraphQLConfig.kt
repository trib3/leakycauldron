package com.trib3.graphql

import com.trib3.config.ConfigLoader
import com.trib3.config.extract
import jakarta.inject.Inject

class GraphQLConfig
@Inject constructor(loader: ConfigLoader) {
    val keepAliveIntervalSeconds: Long
    val idleTimeout: Long?
    val maxBinaryMessageSize: Long?
    val maxTextMessageSize: Long?
    val checkAuthorization: Boolean
    val connectionInitWaitTimeout: Long

    init {
        val config = loader.load("graphQL")
        keepAliveIntervalSeconds = config.extract("keepAliveIntervalSeconds")
        idleTimeout = config.extract("idleTimeout")
        maxBinaryMessageSize = config.extract("maxBinaryMessageSize")
        maxTextMessageSize = config.extract("maxTextMessageSize")
        checkAuthorization = config.extract("checkAuthorization") ?: config.extract("authorizedWebSocketOnly") ?: false
        connectionInitWaitTimeout = config.extract("connectionInitWaitTimeout")
    }
}
