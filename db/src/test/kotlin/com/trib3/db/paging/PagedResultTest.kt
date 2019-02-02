package com.trib3.db.paging

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.testng.annotations.Test

data class SimpleObject(
    val foo: String,
    val bar: String
)

class PagedResultTest {
    @Test
    fun testConstruction() {
        val objects = listOf(
            SimpleObject("baz", "boo"),
            SimpleObject("blee", "blah")
        )
        val p1 = PagedResult(objects, PageToken("blee", "blah"))
        val p2 = PagedResult.getPagedResult(objects) { PageToken(it.foo, it.bar) }
        assertThat(p1.pageToken).isEqualTo(p2.pageToken)
        assertThat(p1.pageToken?.token).isEqualTo("blee,blah")
        assertThat(p1.data).isEqualTo(p2.data)
    }

    @Test
    fun testNoPage() {
        val objects = listOf<SimpleObject>()
        val p1 = PagedResult(objects, null)
        val p2 = PagedResult.getPagedResult(objects) { PageToken(it.foo, it.bar) }
        assertThat(p1.pageToken).isEqualTo(p2.pageToken)
        assertThat(p1.pageToken).isNull()
        assertThat(p1.data).isEqualTo(p2.data)
    }
}
