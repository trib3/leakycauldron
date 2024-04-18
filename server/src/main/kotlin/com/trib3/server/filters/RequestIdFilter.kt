package com.trib3.server.filters

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.slf4j.MDC
import java.util.UUID

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
        inline fun <T> withRequestId(
            requestId: String? = null,
            block: () -> T,
        ): T {
            val createdId = createRequestId(requestId ?: UUID.randomUUID().toString())
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

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        val clientUUID =
            (request as? HttpServletRequest)?.getHeader(REQUEST_ID_HEADER)?.let {
                try {
                    UUID.fromString(it).toString()
                } catch (e: IllegalArgumentException) {
                    val newId = UUID.randomUUID().toString()
                    log.warn("Ignoring invalidly formatted requestId: $it, and using $newId instead", e)
                    newId
                }
            }
        withRequestId(clientUUID) {
            (response as? HttpServletResponse)?.setHeader(REQUEST_ID_HEADER, getRequestId())
            chain.doFilter(request, response)
        }
    }
}
