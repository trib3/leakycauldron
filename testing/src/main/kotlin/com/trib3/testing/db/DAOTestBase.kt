package com.trib3.testing.db

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import javax.sql.DataSource

/**
 * Base class that provides access to an embedded postgres instance [DataSource]
 * for running tests.  Will run any accessible flyway migrations during [setUp].
 */
open class DAOTestBase {
    lateinit var dataSource: DataSource
    lateinit var ctx: DSLContext
    private lateinit var postgres: EmbeddedPostgres
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
            postgres = EmbeddedPostgres.builder()
                .setOutputRedirector(ProcessBuilder.Redirect.DISCARD)
                .start()
            dataSource = HikariDataSource().apply {
                dataSource = postgres.postgresDatabase
                configureDataSource(this)
            }
            ctx = DSL.using(dataSource, SQLDialect.POSTGRES)
            getFlywayConfiguration().load().migrate()
        }
    }

    @AfterClass
    open fun tearDown() {
        postgres.close()
        (dataSource as HikariDataSource).close()
    }
}
