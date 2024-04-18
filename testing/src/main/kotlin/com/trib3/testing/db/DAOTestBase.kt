package com.trib3.testing.db

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import javax.sql.DataSource

/**
 * Base class that provides access to a TestContainers based postgres instance [DataSource]
 * for running tests.  Will run any accessible flyway migrations during [setUp].
 */
open class DAOTestBase {
    lateinit var dataSource: DataSource
    lateinit var ctx: DSLContext
    private lateinit var database: JdbcDatabaseContainer<*>
    private var inited: Boolean = false

    /**
     * By default use a flyway with default configuration
     * pointing at the postgres [DataSource].  Subclasses
     * can override for additional configuration.
     */
    open fun getFlywayConfiguration(): FluentConfiguration {
        return Flyway.configure().dataSource(dataSource)
    }

    /**
     * By default create a postgres container, but subclasses
     * can override for other implementations
     */
    open fun getDatabaseContainer(): JdbcDatabaseContainer<*> {
        return PostgreSQLContainer("postgres:13.4")
    }

    /**
     * By default configure a POSTGRES dialect, but subclasses
     * can override for other dialects
     */
    open fun getJooqDialect(): SQLDialect {
        return SQLDialect.POSTGRES
    }

    /**
     * By default configure autoCommit to false since
     * we do so in DbConfig, and don't want DAO tests
     * to pass due to autocommit if transactions are
     * not committed by application code.
     */
    open fun configureDataSource(ds: HikariDataSource) {
        ds.isAutoCommit = false
    }

    @BeforeClass
    open fun setUp() {
        if (!inited) {
            inited = true
            database = getDatabaseContainer()
            database.start()
            dataSource =
                HikariDataSource().apply {
                    jdbcUrl = database.jdbcUrl
                    username = database.username
                    password = database.password
                    configureDataSource(this)
                }
            ctx = DSL.using(dataSource, getJooqDialect())
            getFlywayConfiguration().load().migrate()
        }
    }

    @AfterClass
    open fun tearDown() {
        database.stop()
        (dataSource as HikariDataSource).close()
    }
}
