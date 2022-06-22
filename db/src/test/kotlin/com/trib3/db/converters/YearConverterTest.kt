package com.trib3.db.converters

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.testng.annotations.Test
import java.time.Year

class YearConverterTest {
    val converter = YearConverter()

    @Test
    fun testLocalDateToYear() {
        val date = "2019"
        assertThat(converter.from(date)).isEqualTo(Year.of(2019))
        assertThat(converter.from(null)).isNull()
    }

    @Test
    fun testYearToLocalDate() {
        val year = Year.of(2019)
        assertThat(converter.to(year)).isEqualTo("2019")
        assertThat(converter.to(null)).isNull()
    }
}
