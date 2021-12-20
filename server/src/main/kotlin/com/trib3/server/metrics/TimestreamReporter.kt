package com.trib3.server.metrics

import com.codahale.metrics.Counter
import com.codahale.metrics.Counting
import com.codahale.metrics.Gauge
import com.codahale.metrics.Histogram
import com.codahale.metrics.Meter
import com.codahale.metrics.Metered
import com.codahale.metrics.MetricFilter
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Sampling
import com.codahale.metrics.ScheduledReporter
import com.codahale.metrics.Timer
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient
import software.amazon.awssdk.services.timestreamwrite.model.Dimension
import software.amazon.awssdk.services.timestreamwrite.model.Record
import software.amazon.awssdk.services.timestreamwrite.model.WriteRecordsRequest
import java.util.SortedMap
import java.util.concurrent.TimeUnit

private const val TIMESTREAM_MAX_RECORDS = 100

/**
 * [ScheduledReporter] implementation that sends metrics to AWS Timestream.
 */
class TimestreamReporter(
    val timestreamWriteClient: TimestreamWriteClient,
    val databaseName: String,
    val tableName: String,
    val globalDimensionValues: List<Dimension>,
    registry: MetricRegistry,
    name: String,
    filter: MetricFilter,
    rateUnit: TimeUnit,
    durationUnit: TimeUnit
) : ScheduledReporter(
    registry, name, filter, rateUnit, durationUnit
) {

    private val rateFactor = rateUnit.toSeconds(1)
    private val durationFactor = 1.0 / durationUnit.toNanos(1)

    /**
     * Collect all metrics and send to Timestream in batches of 100
     */
    override fun report(
        gauges: SortedMap<String, Gauge<Any>>,
        counters: SortedMap<String, Counter>,
        histograms: SortedMap<String, Histogram>,
        meters: SortedMap<String, Meter>,
        timers: SortedMap<String, Timer>
    ) {
        val records = gauges.flatMap {
            getGaugeRecords(it.key, it.value)
        } + (counters + histograms + meters + timers).flatMap {
            getCounterRecords(it.key, it.value)
        } + (meters + timers).flatMap {
            getMeterRecords(it.key, it.value)
        } + (histograms + timers).flatMap {
            getSamplingRecords(it.key, it.value)
        }

        val recordRequestBuilder = WriteRecordsRequest.builder()
            .databaseName(databaseName)
            .tableName(tableName)
            .commonAttributes(
                Record.builder()
                    .time(System.currentTimeMillis().toString())
                    .timeUnit(software.amazon.awssdk.services.timestreamwrite.model.TimeUnit.MILLISECONDS)
                    .dimensions(
                        globalDimensionValues
                    ).build()
            )

        records.chunked(TIMESTREAM_MAX_RECORDS).forEach { chunk ->
            val recordRequest = recordRequestBuilder.records(chunk).build()
            timestreamWriteClient.writeRecords(recordRequest)
        }
    }

    /**
     * For each [Gauge], send the current `value` as a metric.
     */
    private fun getGaugeRecords(gaugeName: String, gauge: Gauge<*>): List<Record> {
        val gaugeValue = gauge.value
        return if (gaugeValue is Number) {
            listOfNotNull(getRecord("$gaugeName.value" to gaugeValue))
        } else {
            listOf()
        }
    }

    /**
     * For each [Counting] ([Counter], [Meter], [Histogram], and [Timer]), send the current `count` as a metric.
     */
    private fun getCounterRecords(counterName: String, counter: Counting): List<Record> {
        return listOfNotNull(getRecord("$counterName.count" to counter.count))
    }

    /**
     * For each [Metered] ([Meter] and [Timer]), send the `mean_rate`, `m1_rate`, `m5_rate` and `m15_rate`
     * as metrics.
     */
    private fun getMeterRecords(meterName: String, meter: Metered): List<Record> {
        return listOf(
            "mean_rate" to (meter.meanRate * rateFactor),
            "m1_rate" to (meter.oneMinuteRate * rateFactor),
            "m5_rate" to (meter.fiveMinuteRate * rateFactor),
            "m15_rate" to (meter.fifteenMinuteRate * rateFactor)
        ).mapNotNull { getRecord("$meterName.${it.first}" to it.second) }
    }

    /**
     * For each [Sampling] ([Histogram] and [Timer]), send the `mean`, `max`, `min`, `stddev`,
     * and `p50`-`p999` percentiles as metrics.
     */
    private fun getSamplingRecords(samplingName: String, sampling: Sampling): List<Record> {
        val samplingFactor = if (sampling is Timer) {
            durationFactor
        } else {
            1.0
        }
        return listOf(
            "mean" to (sampling.snapshot.mean * samplingFactor),
            "min" to (sampling.snapshot.min * samplingFactor),
            "max" to (sampling.snapshot.max * samplingFactor),
            "stddev" to (sampling.snapshot.stdDev * samplingFactor),
            "p50" to (sampling.snapshot.median * samplingFactor),
            "p75" to (sampling.snapshot.get75thPercentile() * samplingFactor),
            "p95" to (sampling.snapshot.get95thPercentile() * samplingFactor),
            "p98" to (sampling.snapshot.get98thPercentile() * samplingFactor),
            "p99" to (sampling.snapshot.get99thPercentile() * samplingFactor),
            "p999" to (sampling.snapshot.get999thPercentile() * samplingFactor)
        ).mapNotNull { getRecord("$samplingName.${it.first}" to it.second) }
    }

    /**
     * Create a [Record] from a name/value pair, making sure to only
     * send finite metric numbers to Timestream
     */
    private fun getRecord(nameValue: Pair<String, Number>): Record? {
        val doubleRep = nameValue.second.toDouble()
        return if (doubleRep.isFinite()) {
            Record.builder()
                .measureName(nameValue.first)
                .measureValue(doubleRep.toBigDecimal().toPlainString())
                .build()
        } else {
            null
        }
    }
}
