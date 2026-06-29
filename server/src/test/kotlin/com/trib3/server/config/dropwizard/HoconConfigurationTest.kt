package com.trib3.server.config.dropwizard

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.config.ConfigLoader
import com.trib3.server.config.BootstrapConfig
import com.trib3.server.modules.DefaultApplicationModule
import io.dropwizard.configuration.FileConfigurationSourceProvider
import io.dropwizard.core.Configuration
import io.dropwizard.core.server.SimpleServerFactory
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.jetty.HttpConnectorFactory
import org.testng.annotations.Test

class HoconConfigurationTest {
    private val configLoader = ConfigLoader()
    private val injector = BootstrapConfig(configLoader).getInjector(listOf(DefaultApplicationModule()))

    @Test
    fun testHoconFactory() {
        val factoryFactory = HoconConfigurationFactoryFactory<Configuration>(configLoader)
        val mapper = injector.getInstance(ObjectMapper::class.java)
        val factory =
            factoryFactory.create(
                Configuration::class.java,
                Bootstrap<Configuration>(null).validatorFactory.validator,
                mapper,
                "dw",
            )
        val config = factory.build(FileConfigurationSourceProvider(), "ignored")
        // Ensure the admin port is set to test hocon's 9080 instead of default 8080
        assertThat(
            (
                (config.serverFactory as SimpleServerFactory)
                    .connector as HttpConnectorFactory
            ).port,
        ).isEqualTo(9080)
    }
}
