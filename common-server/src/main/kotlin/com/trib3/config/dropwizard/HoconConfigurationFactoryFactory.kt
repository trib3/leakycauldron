package com.trib3.config.dropwizard

import com.fasterxml.jackson.databind.ObjectMapper
import io.dropwizard.configuration.ConfigurationFactory
import io.dropwizard.configuration.ConfigurationFactoryFactory
import javax.validation.Validator

class HoconConfigurationFactoryFactory<T> : ConfigurationFactoryFactory<T> {
    override fun create(
        klass: Class<T>,
        validator: Validator,
        objectMapper: ObjectMapper,
        propertyPrefix: String
    ): ConfigurationFactory<T> {
        return HoconConfigurationFactory<T>(
            klass,
            validator,
            objectMapper
        )
    }

}