package com.trib3.server.filters

import java.util.Base64
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Auth filter for the admin servlet to ensure that it checks against the
 * configured adminAuthToken
 */
class AdminAuthFilter : Filter {
    private var token: String? = null
    private var realm: String = "realm"
    private val base64 = Base64.getDecoder()

    /**
     * Extract the basic auth info from the [ServletRequest] and compare against
     * the configured [token].  If unable to match, then set [HttpServletResponse.SC_UNAUTHORIZED]
     * and throw an Exception to prevent the chain from processing.
     */
    private fun checkToken(request: ServletRequest, response: ServletResponse) {
        val credentials = when (request) {
            is HttpServletRequest -> request.getHeader("Authorization")
            else -> null
        }
        if (credentials != null) {
            val (scheme, encoded) = credentials.split(' ')
            if ("basic" == scheme.lowercase()) {
                val decoded = String(base64.decode(encoded))
                val (_, pass) = decoded.split(":")
                if (pass == token) {
                    return
                }
            }
        }
        // boom
        if (response is HttpServletResponse) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"$realm\"")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
        }
        throw IllegalArgumentException("Invalid credentials")
    }

    /**
     * If there's a configured auth [token], call [checkToken] before resuming the chain processing
     */
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (token != null) {
            checkToken(request, response)
        }
        chain.doFilter(request, response)
    }

    /**
     * Read configured [token] and [realm] from the [FilterConfig]
     */
    override fun init(filterConfig: FilterConfig?) {
        filterConfig?.let {
            token = filterConfig.getInitParameter("token")
            filterConfig.getInitParameter("realm")?.let {
                realm = it
            }
        }
    }

    override fun destroy() = Unit
}
