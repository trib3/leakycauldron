package com.trib3.db.converters

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.testng.annotations.Test
import java.time.LocalDate
import java.time.YearMonth

class YearMonthConverterTest {
    val converter = YearMonthConverter()
    @Test
    fun testLocalDateToYearMonth() {
        val date = LocalDate.of(2019, 3, 4)
        assertThat(converter.from(date)).isEqualTo(YearMonth.of(2019, 3))
        assertThat(converter.from(null)).isNull()
    }

    @Test
    fun testYearMonthToLocalDate() {
        val yearMonth = YearMonth.of(2019, 3)
        assertThat(converter.to(yearMonth)).isEqualTo(LocalDate.of(2019, 3, 1))
        assertThat(converter.to(null)).isNull()
    }
}
