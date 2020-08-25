package com.trib3.server.config.dropwizard

import ch.qos.logback.access.spi.IAccessEvent
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.pattern.PatternLayoutBase
import ch.qos.logback.core.spi.FilterReply
import com.fasterxml.jackson.annotation.JsonTypeName
import io.dropwizard.logging.async.AsyncAppenderFactory
import io.dropwizard.logging.filter.LevelFilterFactory
import io.dropwizard.logging.filter.NullLevelFilterFactory
import io.dropwizard.logging.layout.LayoutFactory
import io.dropwizard.request.logging.LogbackAccessRequestLog
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory
import io.dropwizard.request.logging.async.AsyncAccessEventAppenderFactory
import io.dropwizard.request.logging.layout.LogbackAccessRequestLayout
import org.eclipse.jetty.server.RequestLog
import org.slf4j.LoggerFactory
import java.util.TimeZone
import javax.servlet.http.HttpServletResponse

private const val FAST_RESPONSE_TIME = 200

/**
 * [LogbackAccessRequestLayout] that also includes the request Id read from the response
 * headers, and timestamp in the same layout we use for the regular application log.
 */
class RequestIdLogbackAccessRequestLayout(
    context: LoggerContext,
    timeZone: TimeZone
) : LogbackAccessRequestLayout(context, timeZone) {
    init {
        pattern = "%t{ISO8601,UTC} [%responseHeader{X-Request-Id}] ${this.pattern}"
    }
}

/**
 * Layout factory that returns a [RequestIdLogbackAccessRequestLayout]
 */
class RequestIdLogbackAccessRequestLayoutFactory : LayoutFactory<IAccessEvent> {
    override fun build(context: LoggerContext, timeZone: TimeZone): PatternLayoutBase<IAccessEvent> {
        return RequestIdLogbackAccessRequestLayout(context, timeZone)
    }
}

/**
 * Configure the requestLog to skip logging of fast, OK ping responses to avoid
 * cluttering the logs with, eg, ELB health check requests.  Also set the requestLog
 * layout pattern to include timestamp and requestId prefix.
 */
@JsonTypeName("filtered-logback-access")
class FilteredLogbackAccessRequestLogFactory : LogbackAccessRequestLogFactory() {

    override fun build(name: String): RequestLog {
        // almost the same as super.build(), differences are commented
        val logger =
            LoggerFactory.getLogger("http.request") as Logger
        logger.isAdditive = false

        val context = logger.loggerContext

        val requestLog = LogbackAccessRequestLog()

        val levelFilterFactory: LevelFilterFactory<IAccessEvent> = NullLevelFilterFactory()
        val asyncAppenderFactory: AsyncAppenderFactory<IAccessEvent> = AsyncAccessEventAppenderFactory()
        // set layout factory to include timestamp and requestId prefix
        val layoutFactory: LayoutFactory<IAccessEvent> = RequestIdLogbackAccessRequestLayoutFactory()

        for (output in appenders) {
            requestLog.addAppender(output.build(context, name, layoutFactory, levelFilterFactory, asyncAppenderFactory))
        }

        // add successful ping filter
        requestLog.addFilter(
            object : Filter<IAccessEvent>() {
                override fun decide(event: IAccessEvent): FilterReply {
                    if (
                        event.requestURI == "/app/ping" &&
                        event.statusCode == HttpServletResponse.SC_OK &&
                        event.elapsedTime < FAST_RESPONSE_TIME
                    ) {
                        return FilterReply.DENY
                    }
                    return FilterReply.NEUTRAL
                }
            }
        )
        return requestLog
    }
}
