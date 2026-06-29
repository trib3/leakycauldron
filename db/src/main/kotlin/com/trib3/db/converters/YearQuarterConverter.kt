package com.trib3.db.converters

import org.jooq.impl.AbstractConverter
import org.threeten.extra.YearQuarter

/**
 * jOOQ converter for converting from [String] to [YearQuarter]
 */
class YearQuarterConverter : AbstractConverter<String, YearQuarter>(String::class.java, YearQuarter::class.java) {
    override fun from(stringQuarter: String?): YearQuarter? = stringQuarter?.let(YearQuarter::parse)

    override fun to(yearMonth: YearQuarter?): String? = yearMonth?.let(YearQuarter::toString)
}
