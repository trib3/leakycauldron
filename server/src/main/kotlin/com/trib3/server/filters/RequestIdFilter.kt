package com.trib3.server.filters

import mu.KotlinLogging
import org.slf4j.MDC
import java.util.UUID
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse

private val log = KotlinLogging.logger { }

/**
 * A Filter that decorates the logging context MDC with a unique
 * RequestId for the executing request.  If a request is already
 * available (eg, set by AWS Lambda in serverless mode), it will
 * leave that in place and not replace it.  In either case, the
 * response HTTP headers will include an `X-Request-Id` with the
 * RequestId value in it.
 */
class RequestIdFilter : Filter {

    companion object {
        const val REQUEST_ID_KEY = "RequestId"
        const val REQUEST_ID_HEADER = "X-Request-Id"

        /**
         * Convenience function for ensuring a block of code executes with a RequestId set in the MDC.
         *
         * @param requestId the RequestId to set, defaults to a new random UUID
         * @param block the block of code to execute
         */
        fun <T> withRequestId(requestId: String = UUID.randomUUID().toString(), block: () -> T): T {
            val createdId = createRequestId(requestId)
            try {
                return block()
            } finally {
                if (createdId) {
                    clearRequestId()
                }
            }
        }

        /**
         * Set a new RequestId in the MDC, if one is not already set.  If one is already set, ignore the new one.
         *
         * @return whether or not weve set a new RequestId
         */
        fun createRequestId(requestId: String): Boolean {
            val currentMDC: String? = getRequestId()
            if (currentMDC == null) {
                MDC.put(REQUEST_ID_KEY, requestId)
                return true
            }
            return false
        }

        /**
         * Return the current MDC's RequestId, if set
         */
        fun getRequestId(): String? {
            return MDC.get(REQUEST_ID_KEY)
        }

        /**
         * Remove any RequestId set from the MDC
         */
        fun clearRequestId() {
            MDC.remove(REQUEST_ID_KEY)
        }
    }

    override fun init(filterConfig: FilterConfig?) = Unit

    override fun destroy() = Unit

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        withRequestId {
            when (response) {
                is HttpServletResponse -> response.setHeader(REQUEST_ID_HEADER, getRequestId())
                else -> log.warn("Couldn't set request id header for {}", response::class.java)
            }
            chain.doFilter(request, response)
        }
    }
}
