package com.trib3.config.dropwizard

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import com.jasonclawson.jackson.dataformat.hocon.HoconFactory
import com.trib3.config.ConfigLoader
import io.dropwizard.configuration.ConfigurationFactory
import io.dropwizard.configuration.ConfigurationSourceProvider
import javax.validation.Validator

class HoconConfigurationFactory<T>(
    val klass: Class<T>,
    val validator: Validator,
    mapper: ObjectMapper,
    val propertyPrefix: String
): ConfigurationFactory<T> {

    val hoconFactory = HoconFactory()
    val objectMapper = mapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    /**
     * Just rely on default hocon rules for loading application.conf
     */
    override fun build(provider: ConfigurationSourceProvider, path: String): T {
        return build()
    }

    override fun build(): T {
        val configRoot = ConfigLoader.load().root()
        val node: JsonNode = objectMapper.readTree(hoconFactory.createParser(configRoot.render()))
        val config = objectMapper.readValue(TreeTraversingParser(node), klass)
        validator.validate(config)
        return config
    }
}