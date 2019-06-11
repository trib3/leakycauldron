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
        private fun readVersion(): Pair<String, Boolean> {
            return try {
                val loader = this::class.java.classLoader
                val info = loader.getResource("package-info.txt").readText().trim()
                val gitProps = Properties()
                loader.getResourceAsStream("$info.git.properties").use {
                    gitProps.load(it)
                }
                val mavenVersion = gitProps.getProperty("git.build.version")
                val gitBranch = gitProps.getProperty("git.branch")
                val gitCommit = gitProps.getProperty("git.commit.id.abbrev")
                "$mavenVersion $gitBranch-$gitCommit" to true
            } catch (e: Throwable) {
                log.error("Unable to read version info: ${e.message}", e)
                "Unable to read version info: ${e.message}" to false
            }
        }

        private val info: String
        private val healthy: Boolean

        init {
            val versionInfo = readVersion()
            info = versionInfo.first
            healthy = versionInfo.second
        }
    }

    /**
     * Returns version information as a string
     */
    fun info(): String {
        return info
    }

    public override fun check(): Result {
        val resultBuilder = Result.builder().withMessage(info)
        return when (healthy) {
            true -> resultBuilder.healthy().build()
            else -> resultBuilder.unhealthy().build()
        }
    }
}
