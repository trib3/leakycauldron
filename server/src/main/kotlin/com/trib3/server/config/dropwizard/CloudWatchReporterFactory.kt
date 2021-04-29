package com.trib3.server.config.dropwizard

import com.codahale.metrics.MetricRegistry
import com.fasterxml.jackson.annotation.JacksonInject
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeName
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.runIf
import io.dropwizard.metrics.BaseReporterFactory
import io.github.azagniotov.metrics.reporter.cloudwatch.CloudWatchReporter
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import java.net.InetAddress

/**
 * [BaseReporterFactory] that creates a [CloudWatchReporter] based
 * on the dropwizard configuration.
 *
 * Use [includes] and [excludes] to control which attributes get
 * reported to CloudWatch.
 */
@JsonTypeName("cloudwatch")
class CloudWatchReporterFactory(
    @JacksonInject @JsonIgnore
    private val appConfig: TribeApplicationConfig,
    @JacksonInject @JsonIgnore
    internal val cloudwatch: CloudWatchAsyncClient
) : BaseReporterFactory() {
    private val hostname = InetAddress.getLocalHost().hostName

    /**
     * Sets the cloudwatch namespace, defaults to the [TribeApplicationConfig.env] if unspecified
     */
    @JsonProperty
    private val namespace: String? = null

    /**
     * Sets dimension values (name=value formatted strings) on all metrics.
     * Will automatically include Application=[TribeApplicationConfig.appName]
     * and Hostname=${InetAddress.getLocalHost().hostname} as dimensions.
     */
    @JsonProperty
    private val globalDimensions = listOf<String>()

    /**
     * Allows config to override the default percentiles (0.75, 0.95 and 0.999) reported
     * for [com.codahale.metrics.Histogram]s and [com.codahale.metrics.Timer]s
     */
    @JsonProperty
    private val percentiles = listOf<CloudWatchReporter.Percentile>()

    /**
     * Log instead of actually reporting metrics to CloudWatch
     */
    @JsonProperty
    private val dryRun = false

    /**
     * Send mean rates for [com.codahale.metrics.Meter]s and [com.codahale.metrics.Timer]s
     */
    @JsonProperty
    private val meanRate = false

    /**
     * Send one minute mean rates for [com.codahale.metrics.Meter]s and [com.codahale.metrics.Timer]s
     */
    @JsonProperty
    private val oneMinuteMeanRate = false

    /**
     * Send five minute mean rates for [com.codahale.metrics.Meter]s and [com.codahale.metrics.Timer]s
     */
    @JsonProperty
    private val fiveMinuteMeanRate = false

    /**
     * Send fifteen minute mean rates for [com.codahale.metrics.Meter]s and [com.codahale.metrics.Timer]s
     */
    @JsonProperty
    private val fifteenMinuteMeanRate = false

    /**
     * Send arithmetic mean of [com.codahale.metrics.Snapshot] values of
     * [com.codahale.metrics.Histogram]s and [com.codahale.metrics.Timer]s
     */
    @JsonProperty
    private val arithmeticMean = false

    /**
     * Send standard deviation of [com.codahale.metrics.Snapshot] values of
     * [com.codahale.metrics.Histogram]s and [com.codahale.metrics.Timer]s
     */
    @JsonProperty
    private val stdDev = false

    /**
     * Send [com.codahale.metrics.Snapshot] summary values of
     * [com.codahale.metrics.Histogram]s and [com.codahale.metrics.Timer]s
     * as [software.amazon.awssdk.services.cloudwatch.model.StatisticSet]s
     */
    @JsonProperty
    private val statisticSet = false

    /**
     * Post all values, including zeros, to CloudWatch
     */
    @JsonProperty
    private val zeroValueSubmission = false

    /**
     * Send metrics as high resolution (1-second storage resolution)
     * instead of standard resolution (60-second storage resolution)
     */
    @JsonProperty
    private val highResolution = false

    /**
     * Report on raw metric values instead of the change in value from last report time
     */
    @JsonProperty
    private val reportRawCountValue = false

    /**
     * Add additional JVM metrics to the reported metrics.
     */
    @JsonProperty
    private val jvmMetrics = false

    /**
     * Convert [com.codahale.metrics.Meter]s to this unit instead of the [durationUnit]
     */
    @JsonProperty
    private val meterUnit: StandardUnit? = null

    override fun build(registry: MetricRegistry): CloudWatchReporter {
        val finalDimensions = globalDimensions + listOf("Hostname=$hostname", "Application=${appConfig.appName}")
        return CloudWatchReporter.forRegistry(registry, cloudwatch, namespace ?: appConfig.env)
            .withGlobalDimensions(*finalDimensions.toTypedArray())
            .convertDurationsTo(durationUnit)
            .convertRatesTo(rateUnit)
            .filter(filter)
            .runIf(dryRun) {
                withDryRun()
            }.runIf(oneMinuteMeanRate) {
                withOneMinuteMeanRate()
            }.runIf(fiveMinuteMeanRate) {
                withFiveMinuteMeanRate()
            }.runIf(fifteenMinuteMeanRate) {
                withFifteenMinuteMeanRate()
            }.runIf(meanRate) {
                withMeanRate()
            }.runIf(arithmeticMean) {
                withArithmeticMean()
            }.runIf(stdDev) {
                withStdDev()
            }.runIf(statisticSet) {
                withStatisticSet()
            }.runIf(zeroValueSubmission) {
                withZeroValuesSubmission()
            }.runIf(highResolution) {
                withHighResolution()
            }.runIf(jvmMetrics) {
                withJvmMetrics()
            }.runIf(reportRawCountValue) {
                withReportRawCountValue()
            }.runIf(percentiles.isNotEmpty()) {
                withPercentiles(*percentiles.toTypedArray())
            }.runIf(meterUnit != null) {
                withMeterUnitSentToCW(meterUnit)
            }.build()
    }
}
