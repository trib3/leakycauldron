package com.trib3.server.graphql

import com.trib3.config.ConfigLoader
import com.trib3.config.extract
import javax.inject.Inject

class GraphQLConfig
@Inject constructor(loader: ConfigLoader) {
    val keepAliveIntervalSeconds: Long
    val webSocketSubProtocol: String

    init {
        val config = loader.load()
        keepAliveIntervalSeconds = config.extract("graphQL.keepAliveIntervalSeconds")
        webSocketSubProtocol = config.extract("graphQL.webSocketSubProtocol")
    }
}
