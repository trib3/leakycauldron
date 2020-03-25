package com.trib3.db.modules

import com.trib3.db.flyway.FlywayBundle
import dev.misfitlabs.kotlinguice4.KotlinModule
import dev.misfitlabs.kotlinguice4.multibindings.KotlinMultibinder
import io.dropwizard.Configuration
import io.dropwizard.ConfiguredBundle

/**
 * Module that binds in a Flyway Dropwizard Bundle to run db migrations at
 * application startup time
 */
class FlywayModule : KotlinModule() {
    override fun configure() {
        install(DbModule())
        KotlinMultibinder.newSetBinder<ConfiguredBundle<Configuration>>(kotlinBinder).addBinding().to<FlywayBundle>()
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean {
        return other is FlywayModule
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}
