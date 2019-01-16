package com.trib3.healthchecks

import com.codahale.metrics.health.HealthCheck

class VersionHealthCheck : HealthCheck() {

    fun info(): String {
        val pack = VersionHealthCheck::class.java.`package`
        return pack.specificationVersion + " " + pack.implementationVersion
    }

    override fun check(): Result {
        return Result.healthy(info())
    }
}