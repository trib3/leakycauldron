package com.trib3.server.config.dropwizard

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.config.ConfigLoader
import com.trib3.json.modules.ObjectMapperModule
import com.trib3.testing.LeakyMock
import dev.misfitlabs.kotlinguice4.KotlinModule
import io.dropwizard.validation.BaseValidator
import org.easymock.Capture
import org.easymock.EasyMock
import org.testng.annotations.Guice
import org.testng.annotations.Test
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

/**
 * Extension function to make it easier to find the Type dimension
 * in a MetricDatum for test purposes
 */
fun MetricDatum.typeDimension(): String? {
    return this.dimensions().find { it.name() == "Type" }?.value()
}

/**
 * Base test module that configures a mock cloudwatch that expects
 * to have metric data put during the execution of the test.
 * Tests using this module can @[Inject] a [Capture<PutMetricDataRequest>]
 * and perform assertions on the captured metric data.
 *
 * Note that each test below has its own unique subclass of this
 * module so that guice bound values are not shared between tests.
 */
open class MockCloudWatchModule : KotlinModule() {
    override fun configure() {
        val capture = EasyMock.newCapture<PutMetricDataRequest>()
        bind<Capture<PutMetricDataRequest>>().toInstance(capture)
        val mockCloudWatch = LeakyMock.mock<CloudWatchAsyncClient>()
        EasyMock.expect(mockCloudWatch.putMetricData(EasyMock.capture(capture)))
            .andReturn(CompletableFuture.completedFuture(null)).atLeastOnce()
        EasyMock.replay(mockCloudWatch)
        bind<CloudWatchAsyncClient>().toInstance(mockCloudWatch)
        install(ObjectMapperModule())
    }
}

/**
 * Base class with methods for running a cloudwatch metric report(),
 * after which assertions can be made on the catpured metric data
 */
open class CloudWatchReporterFactoryTestBase(val mapper: ObjectMapper) {
    fun runReporter(testCaseConfigPath: String) {
        val configFactory = HoconConfigurationFactory(
            CloudWatchReporterFactory::class.java,
            BaseValidator.newValidator(),
            mapper,
            ConfigLoader(testCaseConfigPath)
        )
        val factory = configFactory.build()
        factory.build(getMetricRegistry()).report()
        EasyMock.verify(factory.cloudwatch)
    }

    fun getMetricRegistry(): MetricRegistry {
        return MetricRegistry().also {
            // create all types of metrics
            it.gauge("g1") {
                Gauge { 1 }
            }
            it.counter("c1").inc()
            it.counter("c2").inc()
            it.histogram("h1").update(1)
            it.meter("m1").mark()
            it.meter("m1").mark()
            it.timer("t1").time().stop()
        }
    }
}

class MockCloudWatchModuleDefault : MockCloudWatchModule()

@Guice(modules = [MockCloudWatchModuleDefault::class])
class CloudWatchReporterFactoryDefaultTest @Inject constructor(
    mapper: ObjectMapper,
    val putCapture: Capture<PutMetricDataRequest>
) : CloudWatchReporterFactoryTestBase(mapper) {
    @Test
    fun testDefaultConfig() {
        runReporter("emptyTestCase")
        val metricData = putCapture.value.metricData()
        assertThat(putCapture.value.namespace()).isEqualTo("dev")
        assertThat(metricData.size).isEqualTo(11)
        metricData.forEach {
            assertThat(it.storageResolution()).isEqualTo(60)
            assertThat(
                it.dimensions().first { d -> d.name() == "Hostname" }
                    ?.value()
            ).isEqualTo(InetAddress.getLocalHost().hostName)
            assertThat(
                it.dimensions().first { d -> d.name() == "Application" }
                    ?.value()
            ).isEqualTo("Test")
        }
        assertThat(metricData[0].metricName()).isEqualTo("g1")
        assertThat(metricData[0].typeDimension()).isEqualTo("gauge")
        assertThat(metricData[1].metricName()).isEqualTo("c1")
        assertThat(metricData[1].typeDimension()).isEqualTo("count")
        assertThat(metricData[2].metricName()).isEqualTo("h1")
        assertThat(metricData[2].typeDimension()).isEqualTo("count")
        assertThat(metricData[3].metricName()).isEqualTo("h1")
        assertThat(metricData[3].typeDimension()).isEqualTo("75%")
        assertThat(metricData[4].metricName()).isEqualTo("h1")
        assertThat(metricData[4].typeDimension()).isEqualTo("95%")
        assertThat(metricData[5].metricName()).isEqualTo("h1")
        assertThat(metricData[5].typeDimension()).isEqualTo("99.9%")
        assertThat(metricData[6].metricName()).isEqualTo("m1")
        assertThat(metricData[6].typeDimension()).isEqualTo("count")
        assertThat(metricData[6].unit()).isEqualTo(StandardUnit.COUNT)
        assertThat(metricData[7].metricName()).isEqualTo("t1")
        assertThat(metricData[7].typeDimension()).isEqualTo("count")
        assertThat(metricData[8].metricName()).isEqualTo("t1")
        assertThat(metricData[8].typeDimension()).isEqualTo("75%")
        assertThat(metricData[9].metricName()).isEqualTo("t1")
        assertThat(metricData[9].typeDimension()).isEqualTo("95%")
        assertThat(metricData[10].metricName()).isEqualTo("t1")
        assertThat(metricData[10].typeDimension()).isEqualTo("99.9%")
    }
}

class MockCloudWatchModuleNamespace : MockCloudWatchModule()

@Guice(modules = [MockCloudWatchModuleNamespace::class])
class CloudWatchReporterFactoryNamespaceTest @Inject constructor(
    mapper: ObjectMapper,
    val putCapture: Capture<PutMetricDataRequest>
) : CloudWatchReporterFactoryTestBase(mapper) {
    @Test
    fun testNamespace() {
        runReporter("namespaceTestCase")
        assertThat(putCapture.value.namespace()).isEqualTo("overrideNamespace")
    }
}

class MockCloudWatchModuleMeterRates : MockCloudWatchModule()

@Guice(modules = [MockCloudWatchModuleMeterRates::class])
class CloudWatchReporterFactoryMeterRatesTest @Inject constructor(
    mapper: ObjectMapper,
    val putCapture: Capture<PutMetricDataRequest>
) : CloudWatchReporterFactoryTestBase(mapper) {
    @Test
    fun testMeterRates() {
        runReporter("meterRatesTestCase")
        val metricData = putCapture.value.metricData()
        assertThat(metricData.size).isEqualTo(5)
        putCapture.value.metricData().forEach {
            assertThat(it.metricName()).isEqualTo("m1")
            if (it.dimensions().find { d -> d.name() == "Type" }?.value() == "count") {
                assertThat(it.unit()).isEqualTo(StandardUnit.COUNT)
            } else {
                assertThat(it.unit()).isEqualTo(StandardUnit.MILLISECONDS)
            }
        }
        assertThat(metricData[0].typeDimension()).isEqualTo("count")
        assertThat(metricData[1].typeDimension()).isEqualTo("1-min-mean-rate [per-second]")
        assertThat(metricData[2].typeDimension()).isEqualTo("5-min-mean-rate [per-second]")
        assertThat(metricData[3].typeDimension()).isEqualTo("15-min-mean-rate [per-second]")
        assertThat(metricData[4].typeDimension()).isEqualTo("mean-rate [per-second]")
    }
}

class MockCloudWatchModuleHistogramTimer : MockCloudWatchModule()

@Guice(modules = [MockCloudWatchModuleHistogramTimer::class])
class CloudWatchReporterFactoryHistogramTimerTest @Inject constructor(
    mapper: ObjectMapper,
    val putCapture: Capture<PutMetricDataRequest>
) : CloudWatchReporterFactoryTestBase(mapper) {
    @Test
    fun testHistogramTimer() {
        runReporter("histoTimerTestCase")
        val metricData = putCapture.value.metricData()
        assertThat(metricData.size).isEqualTo(12)
        assertThat(metricData[0].metricName()).isEqualTo("h1")
        assertThat(metricData[0].typeDimension()).isEqualTo("count")
        assertThat(metricData[1].metricName()).isEqualTo("h1")
        assertThat(metricData[1].typeDimension()).isEqualTo("50%")
        assertThat(metricData[2].metricName()).isEqualTo("h1")
        assertThat(metricData[2].typeDimension()).isEqualTo("99%")
        assertThat(metricData[3].metricName()).isEqualTo("h1")
        assertThat(metricData[3].typeDimension()).isEqualTo("snapshot-mean")
        assertThat(metricData[4].metricName()).isEqualTo("h1")
        assertThat(metricData[4].typeDimension()).isEqualTo("snapshot-std-dev")
        assertThat(metricData[5].metricName()).isEqualTo("h1")
        assertThat(metricData[5].typeDimension()).isEqualTo("snapshot-summary")
        assertThat(metricData[6].metricName()).isEqualTo("t1")
        assertThat(metricData[6].typeDimension()).isEqualTo("count")
        assertThat(metricData[7].metricName()).isEqualTo("t1")
        assertThat(metricData[7].typeDimension()).isEqualTo("50%")
        assertThat(metricData[8].metricName()).isEqualTo("t1")
        assertThat(metricData[8].typeDimension()).isEqualTo("99%")
        assertThat(metricData[9].metricName()).isEqualTo("t1")
        assertThat(metricData[9].typeDimension()).isEqualTo("snapshot-mean [in-milliseconds]")
        assertThat(metricData[10].metricName()).isEqualTo("t1")
        assertThat(metricData[10].typeDimension()).isEqualTo("snapshot-std-dev [in-milliseconds]")
        assertThat(metricData[11].metricName()).isEqualTo("t1")
        assertThat(metricData[11].typeDimension()).isEqualTo("snapshot-summary")
    }
}

class MockCloudWatchModuleJvmMetrics : MockCloudWatchModule()

@Guice(modules = [MockCloudWatchModuleJvmMetrics::class])
class CloudWatchReporterFactoryJvmMetricsTest @Inject constructor(
    mapper: ObjectMapper,
    val putCapture: Capture<PutMetricDataRequest>
) : CloudWatchReporterFactoryTestBase(mapper) {
    @Test
    fun testJvmMetrics() {
        runReporter("jvmMetricsTestCase")
        val metricData = putCapture.value.metricData()
        assertThat(metricData.filter { it.metricName().startsWith("jvm.") }).isNotEmpty()
    }
}

class MockCloudWatchModuleRawCount : MockCloudWatchModule()
class MockCloudWatchModuleNonRawCount : MockCloudWatchModule()

@Guice(modules = [MockCloudWatchModuleNonRawCount::class])
class CloudWatchReporterFactoryNonRawCountTest @Inject constructor(
    mapper: ObjectMapper,
    val putCapture: Capture<PutMetricDataRequest>
) : CloudWatchReporterFactoryTestBase(mapper) {
    @Test
    fun testNonRawCount() {
        val factory = HoconConfigurationFactory(
            CloudWatchReporterFactory::class.java,
            BaseValidator.newValidator(),
            mapper,
            ConfigLoader("nonRawCountTestCase")
        ).build()
        val registry = getMetricRegistry()
        val reporter = factory.build(registry)
        reporter.report()
        registry.counter("c1").inc()
        reporter.report()
        EasyMock.verify(factory.cloudwatch)
        assertThat(putCapture.value.metricData()[0].value()).isEqualTo(1.0)
    }
}

@Guice(modules = [MockCloudWatchModuleRawCount::class])
class CloudWatchReporterFactoryRawCountTest @Inject constructor(
    mapper: ObjectMapper,
    val putCapture: Capture<PutMetricDataRequest>
) : CloudWatchReporterFactoryTestBase(mapper) {

    @Test
    fun testRawCount() {
        val factory = HoconConfigurationFactory(
            CloudWatchReporterFactory::class.java,
            BaseValidator.newValidator(),
            mapper,
            ConfigLoader("rawCountTestCase")
        ).build()
        val registry = getMetricRegistry()
        val reporter = factory.build(registry)
        reporter.report()
        registry.counter("c1").inc()
        reporter.report()
        EasyMock.verify(factory.cloudwatch)
        assertThat(putCapture.value.metricData()[0].value()).isEqualTo(2.0)
    }
}

@Guice(modules = [MockCloudWatchModule::class])
class CloudWatchReporterFactoryHighResolutionTest @Inject constructor(
    mapper: ObjectMapper,
    val putCapture: Capture<PutMetricDataRequest>
) : CloudWatchReporterFactoryTestBase(mapper) {
    @Test
    fun testHighResolution() {
        runReporter("highResolutionTestCase")
        val metricData = putCapture.value.metricData()
        assertThat(putCapture.value.namespace()).isEqualTo("dev")
        assertThat(metricData.size).isEqualTo(11)
        metricData.forEach {
            assertThat(it.storageResolution()).isEqualTo(1)
        }
    }
}

class MockCloudWatchDryRunModule : KotlinModule() {
    override fun configure() {
        val mockCloudWatch = LeakyMock.mock<CloudWatchAsyncClient>()
        EasyMock.replay(mockCloudWatch)
        bind<CloudWatchAsyncClient>().toInstance(mockCloudWatch)
        install(ObjectMapperModule())
    }
}

@Guice(modules = [MockCloudWatchDryRunModule::class])
class CloudWatchReporterDryRunFactoryTest @Inject constructor(
    mapper: ObjectMapper
) : CloudWatchReporterFactoryTestBase(mapper) {

    @Test
    fun testDryRun() {
        runReporter("dryRunTestCase")
    }
}
