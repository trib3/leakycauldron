package com.trib3.server.logging

import mu.KotlinLogging
import org.slf4j.MDC
import java.util.UUID
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse

private val log = KotlinLogging.logger {  }

/**
 * A Filter that decorates the logging context MDC with a unique
 * RequestId for the executing request.  If a request is already
 * available (eg, set by AWS Lambda in serverless mode), it will
 * leave that in place and not replace it.  In either case, the
 * response HTTP headers will include an `X-Request-Id` with the
 * RequestId value in it.
 */
class RequestIdFilter: Filter {

    companion object {
        const val REQUEST_ID_KEY = "RequestId"
        const val REQUEST_ID_HEADER = "X-Request-Id"
    }

    override fun init(filterConfig: FilterConfig?) {
    }

    override fun destroy() {
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val currentMDC: String? = MDC.get(REQUEST_ID_KEY)
        if (currentMDC == null) {
            MDC.put(REQUEST_ID_KEY, UUID.randomUUID().toString())
        }
        try {
            when (response) {
                is HttpServletResponse -> {
                    response.setHeader(REQUEST_ID_HEADER, MDC.get(REQUEST_ID_KEY))
                }
                else -> {
                    log.warn("Couldn't set request id header for {}", response::class.java)
                }
            }
            chain.doFilter(request, response)
        } finally {
            if (currentMDC == null) {
                MDC.remove(REQUEST_ID_KEY)
            }
        }
    }

}
