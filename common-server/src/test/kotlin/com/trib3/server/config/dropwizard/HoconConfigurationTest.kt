package com.trib3.server.config.dropwizard

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.trib3.config.ConfigLoader
import com.trib3.config.KMSStringSelectReader
import com.trib3.json.ObjectMapperProvider
import io.dropwizard.Configuration
import io.dropwizard.configuration.FileConfigurationSourceProvider
import io.dropwizard.jetty.HttpConnectorFactory
import io.dropwizard.server.SimpleServerFactory
import io.dropwizard.setup.Bootstrap
import org.testng.annotations.Test

class HoconConfigurationTest {
    @Test
    fun testHoconFactory() {
        val factoryFactory = HoconConfigurationFactoryFactory<Configuration>(ConfigLoader(KMSStringSelectReader(null)))
        val factory = factoryFactory.create(
            Configuration::class.java,
            Bootstrap<Configuration>(null).validatorFactory.validator,
            ObjectMapperProvider().get(),
            "dw"
        )
        val config = factory.build(FileConfigurationSourceProvider(), "ignored")
        // Ensure the admin port is set to test hocon's 9080 instead of default 8080
        assertThat(
            ((config.serverFactory as SimpleServerFactory)
                .connector as HttpConnectorFactory)
                .port
        ).isEqualTo(9080)
    }
}
