package com.trib3.db.config

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import com.trib3.config.ConfigLoader
import com.trib3.config.extract
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import javax.inject.Inject
import javax.sql.DataSource

private const val POSTGRES_DEFAULT_PORT = 5432

class DbConfig
@Inject constructor(
    loader: ConfigLoader,
    configPath: String,
    healthCheckRegistry: HealthCheckRegistry,
    metricRegistry: MetricRegistry
) {
    val dialect: SQLDialect
    val dataSource: DataSource
    val dslContext: DSLContext

    init {
        val config = loader.load(configPath)

        val subprotocol = config.extract("subprotocol") ?: "postgresql"
        val host = config.extract("host") ?: "localhost"
        val port = config.extract("port") ?: POSTGRES_DEFAULT_PORT
        val schema: String = config.extract("schema") ?: ""
        val driverClassName = config.extract("driverClassName") ?: "org.postgresql.Driver"
        val username = config.extract("user") ?: "tribe"
        val password = config.extract<String?>("password")

        val hds = HikariDataSource()
        hds.poolName = configPath
        hds.username = username
        hds.password = password
        hds.driverClassName = driverClassName
        hds.jdbcUrl = "jdbc:$subprotocol://$host:$port/$schema"
        hds.healthCheckRegistry = healthCheckRegistry
        hds.metricRegistry = metricRegistry

        dataSource = hds

        dialect = config.extract("dialect") ?: SQLDialect.POSTGRES_10
        dslContext = DSL.using(dataSource, dialect)
    }
}
