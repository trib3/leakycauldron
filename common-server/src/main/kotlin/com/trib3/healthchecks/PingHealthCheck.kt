package com.trib3.healthchecks

import com.codahale.metrics.health.HealthCheck

/**
 * A simple HealthCheck that always returns healthy
 */
class PingHealthCheck : HealthCheck() {
    override fun check(): Result {
        return Result.healthy("pong")
    }
}