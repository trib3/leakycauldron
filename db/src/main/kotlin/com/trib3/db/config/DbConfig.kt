package com.trib3.db.config

import com.trib3.config.ConfigLoader
import com.trib3.config.extract
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import javax.sql.DataSource

class DbConfig(configPath: String) {
    val dialect: SQLDialect
    val dataSource: DataSource
    val dslContext: DSLContext

    init {
        val config = ConfigLoader.load(configPath)

        val subprotocol = config.extract("subprotocol") ?: "postgresql"
        val host = config.extract("host") ?: "localhost"
        val port = config.extract("port") ?: 5432
        val schema: String = config.extract("schema") ?: ""
        val driverClassName = config.extract("driverClassName") ?: "org.postgresql.Driver"
        val username = config.extract("user") ?: "tribe"
        val password = config.extract<String?>("password")

        dataSource = HikariDataSource()
        dataSource.username = username
        dataSource.password = password
        dataSource.driverClassName = driverClassName
        dataSource.jdbcUrl = "jdbc:$subprotocol://$host:$port/$schema"

        dialect = config.extract("dialect") ?: SQLDialect.POSTGRES_10
        dslContext = DSL.using(dataSource, dialect)
    }
}
