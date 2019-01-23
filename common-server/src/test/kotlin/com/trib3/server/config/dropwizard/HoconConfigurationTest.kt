package com.trib3.server.config.dropwizard

import assertk.assert
import assertk.assertions.isEqualTo
import io.dropwizard.Configuration
import io.dropwizard.configuration.FileConfigurationSourceProvider
import io.dropwizard.jackson.Jackson
import io.dropwizard.jetty.HttpConnectorFactory
import io.dropwizard.server.DefaultServerFactory
import io.dropwizard.setup.Bootstrap
import org.testng.annotations.Test

class HoconConfigurationTest {
    @Test
    fun testHoconFactory() {
        val factoryFactory = HoconConfigurationFactoryFactory<Configuration>()
        val factory = factoryFactory.create(
            Configuration::class.java,
            Bootstrap<Configuration>(null).validatorFactory.validator,
            Jackson.newObjectMapper(),
            "dw"
        )
        val config = factory.build(FileConfigurationSourceProvider(), "ignored")
        // Ensure the admin port is set to hocon's 9080 instead of default 8081
        assert(
            ((config.serverFactory as DefaultServerFactory)
                .adminConnectors.first() as HttpConnectorFactory)
                .port
        ).isEqualTo(9080)
    }
}