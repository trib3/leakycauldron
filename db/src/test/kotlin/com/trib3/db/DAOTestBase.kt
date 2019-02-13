package com.trib3.db

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
 *
 * Note that test-scoped dependencies are NOT transitive, so if you extend this
 * class make sure that the following dependencies are available in your module,
 * in test or compile scopes:
 *
 * <dependency>
 *     <groupId>com.opentable.components</groupId>
 *     <artifactId>otj-pg-embedded</artifactId>
 * </dependency>
 * <dependency>
 *     <groupId>org.flywaydb</groupId>
 *     <artifactId>flyway-core</artifactId>
 * </dependency>
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
            ctx = DSL.using(dataSource, SQLDialect.POSTGRES_10)
            val flyway = Flyway.configure().dataSource(dataSource).load()
            flyway.migrate()
        }
    }

    @AfterClass
    open fun tearDown() {
        postgres.close()
    }
}
