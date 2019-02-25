package com.trib3.server.config.dropwizard

import ch.qos.logback.access.spi.IAccessEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import com.fasterxml.jackson.annotation.JsonTypeName
import io.dropwizard.request.logging.LogbackAccessRequestLog
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory
import org.eclipse.jetty.server.RequestLog
import javax.servlet.http.HttpServletResponse

private const val FAST_RESPONSE_TIME = 200

/**
 * Configure the requestLog to skip logging of fast, OK ping responses to avoid
 * cluttering the logs with, eg, ELB health check requests
 */
@JsonTypeName("filtered-logback-access")
class FilteredLogbackAccessRequestLogFactory : LogbackAccessRequestLogFactory() {

    override fun build(name: String): RequestLog {
        val log = super.build(name) as LogbackAccessRequestLog
        log.addFilter(object : Filter<IAccessEvent>() {
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
        })
        return log
    }
}
