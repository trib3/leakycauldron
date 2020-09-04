package com.trib3.testing.db

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.testng.annotations.AfterClass
import org.testng.annotations.Test
import java.sql.SQLException

class DAOTestBaseTest : DAOTestBase() {
    @Test
    fun testDb() {
        // ensure the db is usable via jooq
        val tableTables =
            ctx.select(DSL.field("table_name"))
                .from("information_schema.tables")
                .where("table_name='tables'").fetch()
        assertThat(tableTables.map { it.get(0) }).all {
            hasSize(1)
            contains("tables")
        }
        // ensure the db is usable via jdbc and configured for no autoCommit
        val autoCommit = dataSource.connection.use { conn ->
            conn.prepareStatement(
                "select table_name from information_schema.tables where table_name = 'tables'"
            ).use { ps ->
                ps.executeQuery().use { rs ->
                    assertThat(rs.next()).isTrue()
                    assertThat(rs.getString(1)).isEqualTo("tables")
                    assertThat(rs.next()).isFalse()
                    conn.autoCommit
                }
            }
        }
        assertThat(autoCommit).isFalse()
        // ensure multiple setup calls don't change the underlying datasource
        val ds = dataSource
        super.setUp()
        assertThat(ds).isSameAs(dataSource)
    }

    @AfterClass
    override fun tearDown() {
        super.tearDown()
        // ensure db is no longer usable via jooq
        assertThat {
            ctx.select(DSL.field("table_name"))
                .from("information_schema.tables")
                .where("table_name='tables'").fetch()
        }.isFailure().isInstanceOf(DataAccessException::class)
        // ensure db is no longer usable via jdbc
        var reached = false
        assertThat {
            dataSource.connection.use {
                reached = true
            }
        }.isFailure().isInstanceOf(SQLException::class)
        assertThat(reached).isFalse()
    }
}
