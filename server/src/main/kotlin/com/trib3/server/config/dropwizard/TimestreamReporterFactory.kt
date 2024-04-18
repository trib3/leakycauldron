package com.trib3.server.config.dropwizard

import com.codahale.metrics.MetricRegistry
import com.fasterxml.jackson.annotation.JacksonInject
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.annotation.OptBoolean
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.metrics.TimestreamReporter
import io.dropwizard.metrics.common.BaseReporterFactory
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteClient
import software.amazon.awssdk.services.timestreamwrite.model.Dimension
import java.net.InetAddress

/**
 * [BaseReporterFactory] that creates a [TimestreamReporter] based
 * on the dropwizard configuration.
 *
 * Use [includes] and [excludes] to control which attributes get
 * reported to AWS Timestream.
 */
@JsonTypeName("timestream")
class TimestreamReporterFactory(
    @JacksonInject(useInput = OptBoolean.FALSE) @JsonIgnore
    private val appConfig: TribeApplicationConfig,
    @JacksonInject(useInput = OptBoolean.FALSE) @JsonIgnore
    internal val timestreamWriteClient: TimestreamWriteClient,
) : BaseReporterFactory() {
    private val hostname = InetAddress.getLocalHost().hostName

    @JsonProperty
    private val databaseName: String = ""

    @JsonProperty
    private val tableName: String = ""

    /**
     * Sets dimension values (name=value formatted strings) on all metrics.
     * Will automatically include application=[TribeApplicationConfig.appName],
     * env=[TribeApplicationConfig.env], and hostname=${InetAddress.getLocalHost().hostname}
     * as dimensions.
     */
    @JsonProperty
    private val globalDimensions = mapOf<String, String>()

    override fun build(registry: MetricRegistry): TimestreamReporter {
        val finalDimensions =
            globalDimensions.map {
                Dimension.builder().name(it.key).value(it.value).build()
            } +
                listOf(
                    Dimension.builder().name("hostname").value(hostname).build(),
                    Dimension.builder().name("application").value(appConfig.appName).build(),
                    Dimension.builder().name("env").value(appConfig.env).build(),
                )
        return TimestreamReporter(
            timestreamWriteClient,
            databaseName,
            tableName,
            finalDimensions,
            registry,
            "timestream-reporter",
            filter,
            rateUnit,
            durationUnit,
        )
    }
}
