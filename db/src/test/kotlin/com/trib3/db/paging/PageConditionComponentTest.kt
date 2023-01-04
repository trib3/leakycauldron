package com.trib3.db.paging

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.trib3.db.paging.PageConditionComponent.Companion.getPageCondition
import org.jooq.impl.DSL
import org.testng.annotations.Test
import java.math.BigDecimal

class PageConditionComponentTest {
    val stringField = DSL.field("foo", String::class.java)
    val decimalField = DSL.field("bar", BigDecimal::class.java)

    @Test
    fun testCondition() {
        val component1 = PageConditionComponent(stringField, "abc") { it }
        val component2 = PageConditionComponent(decimalField, "123.45") { BigDecimal(it) }

        val ascending = getPageCondition(SortDirection.ASC, component1, component2)
        val descending = getPageCondition(SortDirection.DESC, component1, component2)
        assertThat(component1.field).isEqualTo(stringField)
        assertThat(component1.value).isEqualTo("abc")
        assertThat(component1.extractor.invoke("abc")).isEqualTo("abc")
        assertThat(ascending.toString()).all {
            isEqualTo(
                descending.toString().replace("<", ">"),
            )
            // simple assertions on the SQL -- can't inspect the AST easily and asserting on the
            // whole sql string seems fragile against minor formatting changes
            contains("foo > 'abc'")
            contains("foo = 'abc'")
            contains("bar > 123.45")
        }
    }
}
