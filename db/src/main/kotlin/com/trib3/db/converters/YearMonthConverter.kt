package com.trib3.db.converters

import org.jooq.impl.AbstractConverter
import java.time.LocalDate
import java.time.YearMonth

/**
 * jOOQ converter for converting from [LocalDate] to [YearMonth]
 */
class YearMonthConverter : AbstractConverter<LocalDate, YearMonth>(LocalDate::class.java, YearMonth::class.java) {
    override fun from(sqlDate: LocalDate?): YearMonth? {
        return sqlDate?.let(YearMonth::from)
    }

    override fun to(yearMonth: YearMonth?): LocalDate? {
        return yearMonth?.atDay(1)
    }
}
