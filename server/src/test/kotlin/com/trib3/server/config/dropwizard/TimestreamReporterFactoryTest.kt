package com.trib3.server.config.dropwizard

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import com.codahale.metrics.MetricRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.config.ConfigLoader
import com.trib3.json.modules.ObjectMapperModule
import com.trib3.testing.LeakyMock
import dev.misfitlabs.kotlinguice4.KotlinModule
import io.dropwizard.metrics.common.ReporterFactory
import io.dropwizard.validation.BaseValidator
import org.easymock.EasyMock
import org.testng.annotations.Guice
import org.testng.annotations.Test
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient
import java.net.InetAddress
import javax.inject.Inject

class TimestreamReportFactoryTestModule : KotlinModule() {
    override fun configure() {
        val mockClient = LeakyMock.mock<TimestreamWriteClient>()
        EasyMock.replay(mockClient)
        bind<TimestreamWriteClient>().toInstance(mockClient)
        install(ObjectMapperModule())
    }
}

@Guice(modules = [TimestreamReportFactoryTestModule::class])
class TimestreamReporterFactoryTest @Inject constructor(
    val objectMapper: ObjectMapper,
    val mockTimestreamWriteClient: TimestreamWriteClient,
) {
    @Test
    fun testFactory() {
        val registry = MetricRegistry()
        val testCaseConfigLoader = ConfigLoader("TimeStreamReporterFactoryTest")
        val configFactory = HoconConfigurationFactory(
            ReporterFactory::class.java,
            BaseValidator.newValidator(),
            objectMapper,
            testCaseConfigLoader,
        )
        val factory = configFactory.build() as TimestreamReporterFactory
        val reporter = factory.build(registry)
        assertThat(reporter.databaseName).isEqualTo("TestDBName")
        assertThat(reporter.tableName).isEqualTo("TestTableName")
        assertThat(reporter.timestreamWriteClient).isEqualTo(mockTimestreamWriteClient)
        assertThat(factory.timestreamWriteClient).isEqualTo(mockTimestreamWriteClient)
        assertThat(reporter.globalDimensionValues.map { it.name() }).containsAll(
            "hostname",
            "application",
            "env",
            "key1",
        )
        val dimMap = reporter.globalDimensionValues.associate { it.name() to it.value() }
        assertThat(dimMap["application"]).isEqualTo("Test")
        assertThat(dimMap["env"]).isEqualTo("dev")
        assertThat(dimMap["key1"]).isEqualTo("value1")
        assertThat(dimMap["hostname"]).isEqualTo(InetAddress.getLocalHost().hostName)
    }
}
