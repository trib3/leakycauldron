package com.trib3.server.healthchecks

import com.codahale.metrics.health.HealthCheck

/**
 * A simple HealthCheck that always returns healthy
 */
open class PingHealthCheck : HealthCheck() {
    public override fun check(): Result {
        return Result.healthy("pong")
    }
}
