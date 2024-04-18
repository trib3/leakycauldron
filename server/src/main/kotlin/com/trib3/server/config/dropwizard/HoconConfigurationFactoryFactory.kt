package com.trib3.server.config.dropwizard

import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.config.ConfigLoader
import io.dropwizard.configuration.ConfigurationFactory
import io.dropwizard.configuration.ConfigurationFactoryFactory
import jakarta.inject.Inject
import jakarta.validation.Validator

/**
 * Bootstrap hook to allow dropwizard config to be provided by a [HoconConfigurationFactory]
 */
class HoconConfigurationFactoryFactory<T>
    @Inject
    constructor(val configLoader: ConfigLoader) : ConfigurationFactoryFactory<T> {
        override fun create(
            klass: Class<T>,
            validator: Validator,
            objectMapper: ObjectMapper,
            propertyPrefix: String,
        ): ConfigurationFactory<T> {
            return HoconConfigurationFactory<T>(
                klass,
                validator,
                objectMapper,
                configLoader,
            )
        }
    }
