package com.trib3.db.converters

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.testng.annotations.Test
import org.threeten.extra.YearQuarter

class YearQuarterConverterTest {
    val converter = YearQuarterConverter()

    @Test
    fun testStringToYearQuarter() {
        val date = "2019-Q1"
        assertThat(converter.from(date)).isEqualTo(YearQuarter.of(2019, 1))
        assertThat(converter.from(null)).isNull()
    }

    @Test
    fun testYearQuarterToString() {
        val yearQuarter = YearQuarter.of(2019, 2)
        assertThat(converter.to(yearQuarter)).isEqualTo("2019-Q2")
        assertThat(converter.to(null)).isNull()
    }
}
