package com.trib3.db.modules

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.trib3.config.modules.KMSModule
import com.trib3.db.config.DbConfig
import org.jooq.DSLContext
import javax.inject.Singleton
import javax.sql.DataSource

class DbModule(val configPath: String = "db") : AbstractModule() {
    override fun configure() {
        install(KMSModule())
    }

    @Singleton
    @Provides
    fun provideDbConfig(): DbConfig {
        return DbConfig(configPath)
    }

    @Provides
    fun provideDataSource(dbConfig: DbConfig): DataSource {
        return dbConfig.dataSource
    }

    @Provides
    fun provideDSLContext(dbConfig: DbConfig): DSLContext {
        return dbConfig.dslContext
    }
}