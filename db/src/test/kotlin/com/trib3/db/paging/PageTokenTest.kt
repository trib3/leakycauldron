package com.trib3.db.paging

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.trib3.json.ObjectMapperProvider
import org.testng.annotations.Test

class PageTokenTest {
    val token = PageToken("abc,def")
    @Test
    fun testConstruction() {
        val token2 = PageToken("abc", "def")
        val token3 = PageToken(listOf("abc", "def"))
        assertThat(token).all {
            isEqualTo(token2)
            isEqualTo(token3)
        }
        assertThat(token.token).isEqualTo("abc,def")
        assertThat(token.split()).isEqualTo(listOf("abc", "def"))
    }

    @Test
    fun testSerialization() {
        val objectMapper = ObjectMapperProvider().get()
        val serialized = objectMapper.writeValueAsString(token)
        val read = objectMapper.readValue(serialized, PageToken::class.java)
        assertThat(read.token).isEqualTo("abc,def")
    }
}
