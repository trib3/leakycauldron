package com.trib3.testing.db

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
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

    @BeforeClass
    open fun setUp() {
        if (!inited) {
            inited = true
            postgres = EmbeddedPostgres.builder()
                .setOutputRedirector(ProcessBuilder.Redirect.DISCARD)
                .start()
            dataSource = postgres.postgresDatabase
            ctx = DSL.using(dataSource, SQLDialect.POSTGRES)
            val flyway = Flyway.configure().dataSource(dataSource).load()
            flyway.migrate()
        }
    }

    @AfterClass
    open fun tearDown() {
        postgres.close()
    }
}
