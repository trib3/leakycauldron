package com.trib3.db.converters

import org.jooq.impl.AbstractConverter
import java.time.Year

/**
 * jOOQ converter for converting from [String] to [Year]
 */
class YearConverter : AbstractConverter<String, Year>(String::class.java, Year::class.java) {
    override fun from(stringYear: String?): Year? {
        return stringYear?.let(Year::parse)
    }

    override fun to(year: Year?): String? {
        return year?.let(Year::toString)
    }
}
