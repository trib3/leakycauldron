package com.trib3.server.graphql

import com.expedia.graphql.hooks.SchemaGeneratorHooks
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import org.threeten.extra.YearQuarter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import kotlin.reflect.KType

internal val YEAR_MONTH_SCALAR = GraphQLScalarType.newScalar()
    .name("Month")
    .description("Year + Month, for example 2019-01")
    .coercing(object : Coercing<YearMonth, String> {
        private fun parse(input: String): YearMonth {
            return try {
                YearMonth.parse(input)
            } catch (e: Exception) {
                throw CoercingSerializeException("can't parse $input", e)
            }
        }

        override fun parseValue(input: Any): YearMonth {
            return parse(input.toString())
        }

        override fun parseLiteral(input: Any): YearMonth {
            return when (input) {
                is StringValue -> parse(input.value)
                else -> throw CoercingSerializeException("can't parse $input")
            }
        }

        override fun serialize(dataFetcherResult: Any): String {
            return when (dataFetcherResult) {
                is YearMonth -> dataFetcherResult.toString()
                else -> throw CoercingSerializeException("can't serialize ${dataFetcherResult::class}")
            }
        }
    })
    .build()

internal val YEAR_QUARTER_SCALAR = GraphQLScalarType.newScalar()
    .name("Quarter")
    .description("Year + Quarter, for example 2019-Q1")
    .coercing(object : Coercing<YearQuarter, String> {
        private fun parse(input: String): YearQuarter {
            return try {
                YearQuarter.parse(input)
            } catch (e: Exception) {
                throw CoercingSerializeException("can't parse $input", e)
            }
        }

        override fun parseValue(input: Any): YearQuarter {
            return parse(input.toString())
        }

        override fun parseLiteral(input: Any): YearQuarter {
            return when (input) {
                is StringValue -> parse(input.value)
                else -> throw CoercingSerializeException("can't parse $input")
            }
        }

        override fun serialize(dataFetcherResult: Any): String {
            return when (dataFetcherResult) {
                is YearQuarter -> dataFetcherResult.toString()
                else -> throw CoercingSerializeException("can't serialize ${dataFetcherResult::class}")
            }
        }
    })
    .build()

internal val LOCAL_DATETIME_SCALAR = GraphQLScalarType.newScalar()
    .name("LocalDateTime")
    .description("Year + Month + Day Of Month + Time (Hour:Minute + Optional(Second:Milliseconds)), " +
        "for example 2019-10-31T12:31:45.129")
    .coercing(object : Coercing<LocalDateTime, String> {
        private fun parse(input: String): LocalDateTime {
            return try {
                LocalDateTime.parse(input)
            } catch (e: Exception) {
                throw CoercingSerializeException("can't parse $input", e)
            }
        }

        override fun parseValue(input: Any): LocalDateTime {
            return parse(input.toString())
        }

        override fun parseLiteral(input: Any): LocalDateTime {
            return when (input) {
                is StringValue -> parse(input.value)
                else -> throw CoercingSerializeException("can't parse $input")
            }
        }

        override fun serialize(dataFetcherResult: Any): String {
            return when (dataFetcherResult) {
                is LocalDateTime -> dataFetcherResult.toString()
                else -> throw CoercingSerializeException("can't serialize ${dataFetcherResult::class}")
            }
        }
    })
    .build()

internal val LOCAL_DATE_SCALAR = GraphQLScalarType.newScalar()
    .name("LocalDate")
    .description("Year + Month + Day Of Month, for example 2019-10-31")
    .coercing(object : Coercing<LocalDate, String> {
        private fun parse(input: String): LocalDate {
            return try {
                LocalDate.parse(input)
            } catch (e: Exception) {
                throw CoercingSerializeException("can't parse $input", e)
            }
        }

        override fun parseValue(input: Any): LocalDate {
            return parse(input.toString())
        }

        override fun parseLiteral(input: Any): LocalDate {
            return when (input) {
                is StringValue -> parse(input.value)
                else -> throw CoercingSerializeException("can't parse $input")
            }
        }

        override fun serialize(dataFetcherResult: Any): String {
            return when (dataFetcherResult) {
                is LocalDate -> dataFetcherResult.toString()
                else -> throw CoercingSerializeException("can't serialize ${dataFetcherResult::class}")
            }
        }
    })
    .build()

internal val LOCAL_TIME_SCALAR = GraphQLScalarType.newScalar()
    .name("LocalTime")
    .description("Hour + Minute + Optional (Second + Millisecond) , for example 12:31:45.129")
    .coercing(object : Coercing<LocalTime, String> {
        private fun parse(input: String): LocalTime {
            return try {
                LocalTime.parse(input)
            } catch (e: Exception) {
                throw CoercingSerializeException("can't parse $input", e)
            }
        }

        override fun parseValue(input: Any): LocalTime {
            return parse(input.toString())
        }

        override fun parseLiteral(input: Any): LocalTime {
            return when (input) {
                is StringValue -> parse(input.value)
                else -> throw CoercingSerializeException("can't parse $input")
            }
        }

        override fun serialize(dataFetcherResult: Any): String {
            return when (dataFetcherResult) {
                is LocalTime -> dataFetcherResult.toString()
                else -> throw CoercingSerializeException("can't serialize ${dataFetcherResult::class}")
            }
        }
    })
    .build()

internal val OFFSET_DATETIME_SCALAR = GraphQLScalarType.newScalar()
    .name("OffsetDateTime")
    .description("Year + Month + Day Of Month + Time (Hour:Minute + Optional(Second.Milliseconds)) " +
        "+ Offset, for example 2019-10-31T12:31:45.129-07:00")
    .coercing(object : Coercing<OffsetDateTime, String> {
        private fun parse(input: String): OffsetDateTime {
            return try {
                OffsetDateTime.parse(input)
            } catch (e: Exception) {
                throw CoercingSerializeException("can't parse $input", e)
            }
        }

        override fun parseValue(input: Any): OffsetDateTime {
            return parse(input.toString())
        }

        override fun parseLiteral(input: Any): OffsetDateTime {
            return when (input) {
                is StringValue -> parse(input.value)
                else -> throw CoercingSerializeException("can't parse $input")
            }
        }

        override fun serialize(dataFetcherResult: Any): String {
            return when (dataFetcherResult) {
                is OffsetDateTime -> dataFetcherResult.toString()
                else -> throw CoercingSerializeException("can't serialize ${dataFetcherResult::class}")
            }
        }
    })
    .build()

/**
 * Schema generator hooks implementation that defines scalars for java.time (and threeten-extras) objects
 */
class DateTimeHooks : SchemaGeneratorHooks {
    override fun willGenerateGraphQLType(type: KType): GraphQLType? {
        return when (type.classifier) {
            YearMonth::class -> YEAR_MONTH_SCALAR
            YearQuarter::class -> YEAR_QUARTER_SCALAR
            LocalDateTime::class -> LOCAL_DATETIME_SCALAR
            LocalDate::class -> LOCAL_DATE_SCALAR
            LocalTime::class -> LOCAL_TIME_SCALAR
            OffsetDateTime::class -> OFFSET_DATETIME_SCALAR
            else -> null
        }
    }
}
