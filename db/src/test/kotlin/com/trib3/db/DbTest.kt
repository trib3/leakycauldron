package com.trib3.db

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.trib3.db.config.DbConfig
import com.trib3.db.modules.DbModule
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testng.annotations.Guice
import org.testng.annotations.Test
import javax.inject.Inject
import javax.sql.DataSource

/**
 * Test that ensures that the module enables injection of a dslContext that can connect to the database
 * and run simple queries against the information_schema
 */
@Guice(modules = [DbModule::class])
class DbTest
@Inject constructor(
    val dbConfig: DbConfig,
    val dslContext: DSLContext,
    val dataSource: DataSource
) {
    @Test
    fun testDb() {
        val tableNames = dslContext
            .select(DSL.field("table_name")).from("information_schema.tables")
            .where(DSL.field("table_schema").eq("information_schema"))
            .fetch()
            .map { it.get("table_name") }
        assertThat(tableNames).all {
            contains("tables")
            contains("sequences")
            contains("views")
        }
    }

    @Test
    fun testRawJdbc() {
        val row = dataSource.connection.use { conn ->
            conn.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    select table_name from information_schema.tables
                    where table_schema = 'information_schema'
                    and table_name = 'tables'
                    """
                ).use { rs ->
                    rs.next(); rs.getString("table_name")
                }
            }
        }
        assertThat(row).isEqualTo("tables")
    }

    @Test
    fun testConfig() {
        assertThat(dbConfig.dataSource).isEqualTo(this.dataSource)
        assertThat(dbConfig.dslContext).isEqualTo(this.dslContext)
        assertThat(dbConfig.dialect).isEqualTo(SQLDialect.POSTGRES_10)
    }
}
