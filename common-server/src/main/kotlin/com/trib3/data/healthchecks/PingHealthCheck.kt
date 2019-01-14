package com.trib3.data.healthchecks

import com.codahale.metrics.health.HealthCheck

class PingHealthCheck : HealthCheck() {
    override fun check(): Result {
        return Result.healthy("pong")
    }
}