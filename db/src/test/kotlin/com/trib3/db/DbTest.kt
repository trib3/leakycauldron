package com.trib3.db

import assertk.all
import assertk.assert
import assertk.assertions.contains
import com.trib3.db.modules.DbModule
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.testng.annotations.Guice
import org.testng.annotations.Test
import javax.inject.Inject

/**
 * Test that ensures that the module enables injection of a dslContext that can connect to the database
 * and run simple queries against the information_schema
 */
@Guice(modules = [DbModule::class])
class DbTest @Inject constructor(val dslContext: DSLContext) {
    @Test
    fun testDb() {
        val tableNames = dslContext
            .select(DSL.field("table_name")).from("information_schema.tables")
            .where(DSL.field("table_schema").eq("information_schema"))
            .fetch()
            .map { it.get("table_name") }
        assert(tableNames).all {
            contains("tables")
            contains("sequences")
            contains("views")
        }
    }
}