package com.trib3.json.jackson

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.KeyDeserializer
import org.threeten.extra.YearQuarter
import java.time.format.DateTimeParseException

/**
 * Jackson [KeyDeserializer] for [YearQuarter] objects.
 */
class YearQuarterKeyDeserializer : KeyDeserializer() {
    override fun deserializeKey(key: String, ctxt: DeserializationContext): Any {
        return try {
            YearQuarter.parse(key)
        } catch (e: DateTimeParseException) {
            ctxt.handleWeirdKey(YearQuarter::class.java, key, "Unexpected quarter: $key")
        }
    }
}
