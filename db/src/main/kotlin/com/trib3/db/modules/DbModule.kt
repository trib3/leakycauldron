package com.trib3.db.modules

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.trib3.config.ConfigLoader
import com.trib3.config.modules.KMSModule
import com.trib3.db.config.DbConfig
import org.jooq.DSLContext
import javax.inject.Singleton
import javax.sql.DataSource

/**
 * Module that exposes a [DbConfig] and that [DbConfig]'s [DataSource] and [DSLContext] for
 * use in applications.  Assumes the database config is in application.conf at "db"
 */
class DbModule() : AbstractModule() {
    override fun configure() {
        install(KMSModule())
    }

    @Singleton
    @Provides
    fun provideDbConfig(loader: ConfigLoader): DbConfig {
        return DbConfig(loader, "db")
    }

    @Provides
    fun provideDataSource(dbConfig: DbConfig): DataSource {
        return dbConfig.dataSource
    }

    @Provides
    fun provideDSLContext(dbConfig: DbConfig): DSLContext {
        return dbConfig.dslContext
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean {
        return other is DbModule
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}
