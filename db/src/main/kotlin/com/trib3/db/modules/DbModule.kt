package com.trib3.db.modules

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.trib3.config.ConfigLoader
import com.trib3.config.modules.KMSModule
import com.trib3.db.config.DbConfig
import jakarta.inject.Singleton
import org.jooq.DSLContext
import javax.sql.DataSource

/**
 * Module that exposes a [DbConfig] and that [DbConfig]'s [DataSource] and [DSLContext] for
 * use in applications.  Assumes the database config is in application.conf at "db"
 */
class DbModule : AbstractModule() {
    override fun configure() {
        install(KMSModule())
    }

    @Singleton
    @Provides
    fun provideDbConfig(
        loader: ConfigLoader,
        healthCheckRegistry: HealthCheckRegistry,
        metricRegistry: MetricRegistry,
        objectMapper: ObjectMapper,
    ): DbConfig = DbConfig(loader, "db", healthCheckRegistry, metricRegistry, objectMapper)

    @Provides
    fun provideDataSource(dbConfig: DbConfig): DataSource = dbConfig.dataSource

    @Provides
    fun provideDSLContext(dbConfig: DbConfig): DSLContext = dbConfig.dslContext

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean = other is DbModule

    override fun hashCode(): Int = this::class.hashCode()
}
