package com.trib3.server.config.dropwizard

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.trib3.config.ConfigLoader
import com.trib3.json.ObjectMapperProvider
import io.dropwizard.configuration.FileConfigurationSourceProvider
import io.dropwizard.core.Configuration
import io.dropwizard.core.server.SimpleServerFactory
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.jetty.HttpConnectorFactory
import org.testng.annotations.Test

class HoconConfigurationTest {
    @Test
    fun testHoconFactory() {
        val factoryFactory = HoconConfigurationFactoryFactory<Configuration>(ConfigLoader())
        val factory =
            factoryFactory.create(
                Configuration::class.java,
                Bootstrap<Configuration>(null).validatorFactory.validator,
                ObjectMapperProvider().get(),
                "dw",
            )
        val config = factory.build(FileConfigurationSourceProvider(), "ignored")
        // Ensure the admin port is set to test hocon's 9080 instead of default 8080
        assertThat(
            (
                (config.serverFactory as SimpleServerFactory)
                    .connector as HttpConnectorFactory
            )
                .port,
        ).isEqualTo(9080)
    }
}
