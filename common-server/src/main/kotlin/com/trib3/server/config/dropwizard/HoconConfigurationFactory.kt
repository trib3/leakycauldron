package com.trib3.server.config.dropwizard

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import com.jasonclawson.jackson.dataformat.hocon.HoconFactory
import com.trib3.config.ConfigLoader
import io.dropwizard.configuration.ConfigurationFactory
import io.dropwizard.configuration.ConfigurationSourceProvider
import javax.validation.Validator

/**
 * A ConfigurationFactory that returns config instantiated by parsing hocon from application.conf
 */
class HoconConfigurationFactory<T>(
    val klass: Class<T>,
    val validator: Validator,
    mapperArg: ObjectMapper
): ConfigurationFactory<T> {

    val hoconFactory = HoconFactory()
    val mapper = mapperArg.copy()

    init {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    /**
     * Builds the configuration by delegating to [build()]
     */
    override fun build(provider: ConfigurationSourceProvider, path: String): T {
        return build()
    }

    /**
     * Builds the configuration from the configuration loaded by [ConfigLoader.load()]
     */
    override fun build(): T {
        val configRoot = ConfigLoader.load().root()
        val node: JsonNode = mapper.readTree(hoconFactory.createParser(configRoot.render()))
        val config = mapper.readValue(TreeTraversingParser(node), klass)
        validator.validate(config)
        return config
    }
}