package com.trib3.server.healthchecks

import com.codahale.metrics.health.HealthCheck
import mu.KotlinLogging
import java.util.Properties

private val log = KotlinLogging.logger { }

/**
 * A simple HealthCheck that returns runtime version information
 */
class VersionHealthCheck : HealthCheck() {
    companion object {
        private fun readInfo(): String {
            try {
                val loader = this::class.java.classLoader
                val info = loader.getResource("package-info.txt").readText().trim()
                val gitProps = Properties()
                loader.getResourceAsStream("$info.git.properties").use {
                    gitProps.load(it)
                }
                return gitProps.getProperty("git.build.version") + " " + gitProps.getProperty("git.commit.id.abbrev")
            } catch (e: Throwable) {
                log.error("Unable to read version info: ${e.message}", e)
                healthy = false
                return "Unable to read version info: ${e.message}"
            }
        }

        var healthy = true
        val info = readInfo()
    }

    /**
     * Returns version information as a string
     */
    fun info(): String {
        return info
    }

    override public fun check(): Result {
        val resultBuilder = Result.builder().withMessage(info)
        return when (healthy) {
            true -> resultBuilder.healthy().build()
            else -> resultBuilder.unhealthy().build()
        }
    }
}