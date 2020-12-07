package com.trib3.graphql

import com.trib3.config.ConfigLoader
import com.trib3.config.extract
import javax.inject.Inject

class GraphQLConfig
@Inject constructor(loader: ConfigLoader) {
    val keepAliveIntervalSeconds: Long
    val webSocketSubProtocol: String
    val idleTimeout: Long?
    val maxBinaryMessageSize: Int?
    val maxTextMessageSize: Int?
    val checkAuthorization: Boolean

    init {
        val config = loader.load("graphQL")
        keepAliveIntervalSeconds = config.extract("keepAliveIntervalSeconds")
        webSocketSubProtocol = config.extract("webSocketSubProtocol")
        idleTimeout = config.extract("idleTimeout")
        maxBinaryMessageSize = config.extract("maxBinaryMessageSize")
        maxTextMessageSize = config.extract("maxTextMessageSize")
        checkAuthorization = config.extract("checkAuthorization") ?: config.extract("authorizedWebSocketOnly") ?: false
    }
}
