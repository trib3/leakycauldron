package com.trib3.testing.db

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isFailure
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.matches
import assertk.assertions.message
import org.jooq.impl.DSL
import org.testng.annotations.AfterClass
import org.testng.annotations.Test

class DAOTestBaseTest : DAOTestBase() {
    @Test
    fun testDb() {
        // ensure the db is usable
        val tableTables =
            ctx.select(DSL.field("table_name"))
                .from("information_schema.tables")
                .where("table_name='tables'").fetch()
        assertThat(tableTables.map { it.get(0) }).all {
            hasSize(1)
            contains("tables")
        }
        var reached = false
        dataSource.connection.use {
            reached = true
        }
        assertThat(reached).isTrue()
        val ds = dataSource
        super.setUp()
        assertThat(ds).isSameAs(dataSource)
    }

    @AfterClass
    override fun tearDown() {
        super.tearDown()
        assertThat {
            ctx.select(DSL.field("table_name"))
                .from("information_schema.tables")
                .where("table_name='tables'").fetch()
        }.isFailure().message().isNotNull().contains("Error getting connection")
        var reached = false
        assertThat {
            dataSource.connection.use {
                reached = true
            }
        }.isFailure().message().isNotNull().matches(Regex(".*Connection to .* refused.*"))
        assertThat(reached).isFalse()
    }
}
