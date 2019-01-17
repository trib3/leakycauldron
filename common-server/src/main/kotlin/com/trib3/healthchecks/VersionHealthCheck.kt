package com.trib3.healthchecks

import com.codahale.metrics.health.HealthCheck

/**
 * A simple HealthCheck that returns runtime version information
 */
class VersionHealthCheck : HealthCheck() {
    /**
     * Returns version information as a string
     */
    fun info(): String {
        val pack = VersionHealthCheck::class.java.`package`
        return pack.specificationVersion + " " + pack.implementationVersion
    }

    override fun check(): Result {
        return Result.healthy(info())
    }
}