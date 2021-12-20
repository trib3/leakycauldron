package com.trib3.server.metrics

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricFilter
import com.codahale.metrics.MetricRegistry
import com.trib3.testing.LeakyMock
import org.easymock.EasyMock
import org.testng.annotations.Test
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient
import software.amazon.awssdk.services.timestreamwrite.model.Dimension
import software.amazon.awssdk.services.timestreamwrite.model.WriteRecordsRequest
import java.util.concurrent.TimeUnit

class TimestreamReporterTest {
    @Test
    fun testTimestreamReporter() {
        val requestCapture = EasyMock.newCapture<WriteRecordsRequest>()
        val mockClient = LeakyMock.mock<TimestreamWriteClient>()
        EasyMock.expect(mockClient.writeRecords(EasyMock.capture(requestCapture))).andReturn(null)
        EasyMock.replay(mockClient)
        val registry = MetricRegistry()
        registry.gauge("gauge") {
            Gauge { 5 }
        }
        registry.gauge("stringgauge") {
            Gauge { "hi" }
        }
        registry.gauge("nangauge") {
            Gauge { Double.NaN }
        }
        registry.meter("meter").mark()
        registry.meter("meter").mark()
        registry.histogram("histo").update(1)
        registry.timer("timer").time().stop()
        val globalDimensions = listOf(Dimension.builder().name("key").value("val").build())
        val reporter = TimestreamReporter(
            mockClient,
            "dbName",
            "tableName",
            globalDimensions,
            registry,
            "",
            MetricFilter.ALL,
            TimeUnit.MILLISECONDS,
            TimeUnit.SECONDS
        )
        reporter.report()

        EasyMock.verify(mockClient)
        assertThat(requestCapture.value.databaseName()).isEqualTo("dbName")
        assertThat(requestCapture.value.tableName()).isEqualTo("tableName")
        assertThat(requestCapture.value.commonAttributes().time()).isNotNull()
        assertThat(
            requestCapture.value.commonAttributes().timeUnit()
        ).isEqualTo(software.amazon.awssdk.services.timestreamwrite.model.TimeUnit.MILLISECONDS)
        assertThat(requestCapture.value.commonAttributes().dimensions()).isEqualTo(globalDimensions)
        with(requestCapture.value.records()) {
            // assert the known metric values exactly
            assertThat(first { it.measureName() == "gauge.value" }.measureValue()).isEqualTo("5.0")
            assertThat(firstOrNull { it.measureName() == "stringgauge.value" }).isNull()
            assertThat(firstOrNull { it.measureName() == "nangauge.value" }).isNull()
            assertThat(first { it.measureName() == "meter.count" }.measureValue()).isEqualTo("2.0")
            assertThat(first { it.measureName() == "histo.count" }.measureValue()).isEqualTo("1.0")
            assertThat(first { it.measureName() == "timer.count" }.measureValue()).isEqualTo("1.0")
            assertThat(first { it.measureName() == "histo.mean" }.measureValue()).isEqualTo("1.0")
            assertThat(first { it.measureName() == "histo.min" }.measureValue()).isEqualTo("1.0")
            assertThat(first { it.measureName() == "histo.max" }.measureValue()).isEqualTo("1.0")
            assertThat(first { it.measureName() == "histo.stddev" }.measureValue()).isEqualTo("0.0")
            assertThat(first { it.measureName() == "histo.p50" }.measureValue()).isEqualTo("1.0")
            assertThat(first { it.measureName() == "histo.p75" }.measureValue()).isEqualTo("1.0")
            assertThat(first { it.measureName() == "histo.p95" }.measureValue()).isEqualTo("1.0")
            assertThat(first { it.measureName() == "histo.p98" }.measureValue()).isEqualTo("1.0")
            assertThat(first { it.measureName() == "histo.p99" }.measureValue()).isEqualTo("1.0")
            assertThat(first { it.measureName() == "histo.p999" }.measureValue()).isEqualTo("1.0")
            // assert the variable/timing-based metric values exist
            assertThat(filter { it.measureName() == "meter.mean_rate" }).hasSize(1)
            assertThat(filter { it.measureName() == "meter.m1_rate" }).hasSize(1)
            assertThat(filter { it.measureName() == "meter.m5_rate" }).hasSize(1)
            assertThat(filter { it.measureName() == "meter.m15_rate" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.mean_rate" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.m1_rate" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.m5_rate" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.m15_rate" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.mean" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.min" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.max" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.stddev" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.p50" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.p75" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.p95" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.p98" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.p99" }).hasSize(1)
            assertThat(filter { it.measureName() == "timer.p999" }).hasSize(1)
        }
    }
}
