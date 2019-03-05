package com.trib3.json.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import org.threeten.extra.YearQuarter

/**
 * Jackson [JsonDeserializer] for [YearQuarter] objects.
 */
class YearQuarterDeserializer : JsonDeserializer<YearQuarter>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): YearQuarter? {
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            val string = parser.text.trim()
            return if (string.isEmpty()) null else YearQuarter.parse(string)
        }
        return ctxt.handleUnexpectedToken(
            YearQuarter::class.java,
            parser.currentToken,
            parser,
            "Expected VALUE_STRING for YearQuarter but saw ${parser.currentToken}"
        ) as YearQuarter
    }
}
