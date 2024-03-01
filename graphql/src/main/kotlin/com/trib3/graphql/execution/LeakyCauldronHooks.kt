package com.trib3.graphql.execution

import com.expediagroup.graphql.generator.directives.KotlinDirectiveWiringFactory
import com.expediagroup.graphql.generator.directives.KotlinSchemaDirectiveWiring
import com.expediagroup.graphql.generator.hooks.FlowSubscriptionSchemaGeneratorHooks
import graphql.language.StringValue
import graphql.scalars.ExtendedScalars
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import io.dropwizard.auth.Authorizer
import jakarta.inject.Inject
import org.threeten.extra.YearQuarter
import java.math.BigDecimal
import java.security.Principal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.UUID
import javax.annotation.Nullable
import kotlin.reflect.KType

internal val YEAR_SCALAR =
    GraphQLScalarType.newScalar()
        .name("Year")
        .description("Year, for example 2019")
        .coercing(
            object : Coercing<Year, String> {
                private fun parse(
                    input: String,
                    exceptionConstructor: (String, Throwable) -> Exception,
                ): Year {
                    return try {
                        Year.parse(input)
                    } catch (e: Exception) {
                        throw exceptionConstructor("can't parse $input", e)
                    }
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("parseValue(input, context, locale)"))
                override fun parseValue(input: Any): Year {
                    return parse(input.toString(), ::CoercingParseValueException)
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("parseLiteral(input, context, locale)"))
                override fun parseLiteral(input: Any): Year {
                    return when (input) {
                        is StringValue -> parse(input.value, ::CoercingParseLiteralException)
                        else -> throw CoercingParseLiteralException("can't parse $input")
                    }
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("serialize(input, context, locale)"))
                override fun serialize(dataFetcherResult: Any): String {
                    return when (dataFetcherResult) {
                        is Year -> dataFetcherResult.toString()
                        else -> throw CoercingSerializeException("can't serialize ${dataFetcherResult::class}")
                    }
                }
            },
        )
        .build()

internal val YEAR_MONTH_SCALAR =
    GraphQLScalarType.newScalar()
        .name("Month")
        .description("Year + Month, for example 2019-01")
        .coercing(
            object : Coercing<YearMonth, String> {
                private fun parse(
                    input: String,
                    exceptionConstructor: (String, Throwable) -> Exception,
                ): YearMonth {
                    return try {
                        YearMonth.parse(input)
                    } catch (e: Exception) {
                        throw exceptionConstructor("can't parse $input", e)
                    }
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("parseValue(input, context, locale)"))
                override fun parseValue(input: Any): YearMonth {
                    return parse(input.toString(), ::CoercingParseValueException)
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("parseLiteral(input, context, locale)"))
                override fun parseLiteral(input: Any): YearMonth {
                    return when (input) {
                        is StringValue -> parse(input.value, ::CoercingParseLiteralException)
                        else -> throw CoercingParseLiteralException("can't parse $input")
                    }
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("serialize(input, context, locale)"))
                override fun serialize(dataFetcherResult: Any): String {
                    return when (dataFetcherResult) {
                        is YearMonth -> dataFetcherResult.toString()
                        else -> throw CoercingSerializeException("can't serialize ${dataFetcherResult::class}")
                    }
                }
            },
        )
        .build()

internal val YEAR_QUARTER_SCALAR =
    GraphQLScalarType.newScalar()
        .name("Quarter")
        .description("Year + Quarter, for example 2019-Q1")
        .coercing(
            object : Coercing<YearQuarter, String> {
                private fun parse(
                    input: String,
                    exceptionConstructor: (String, Throwable) -> Exception,
                ): YearQuarter {
                    return try {
                        YearQuarter.parse(input)
                    } catch (e: Exception) {
                        throw exceptionConstructor("can't parse $input", e)
                    }
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("parseValue(input, context, locale)"))
                override fun parseValue(input: Any): YearQuarter {
                    return parse(input.toString(), ::CoercingParseValueException)
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("parseLiteral(input, context, locale)"))
                override fun parseLiteral(input: Any): YearQuarter {
                    return when (input) {
                        is StringValue -> parse(input.value, ::CoercingParseLiteralException)
                        else -> throw CoercingParseLiteralException("can't parse $input")
                    }
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("serialize(input, context, locale)"))
                override fun serialize(dataFetcherResult: Any): String {
                    return when (dataFetcherResult) {
                        is YearQuarter -> dataFetcherResult.toString()
                        else -> throw CoercingSerializeException("can't serialize ${dataFetcherResult::class}")
                    }
                }
            },
        )
        .build()

internal val LOCAL_DATETIME_SCALAR =
    GraphQLScalarType.newScalar()
        .name("LocalDateTime")
        .description(
            "Year + Month + Day Of Month + Time (Hour:Minute + Optional(Second:Milliseconds)), " +
                "for example 2019-10-31T12:31:45.129",
        )
        .coercing(
            object : Coercing<LocalDateTime, String> {
                // define iso formatter with required fractional seconds per https://www.graphql-scalars.com/date-time/
                @Suppress("MagicNumber") // const expression
                private val ISO_FORMATTER =
                    DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .append(DateTimeFormatter.ISO_LOCAL_DATE)
                        .appendLiteral('T')
                        .appendValue(ChronoField.HOUR_OF_DAY, 2)
                        .appendLiteral(':')
                        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                        .appendLiteral(':')
                        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                        .appendFraction(ChronoField.NANO_OF_SECOND, 3, 3, true)
                        .toFormatter()

                private fun parse(
                    input: String,
                    exceptionConstructor: (String, Throwable) -> Exception,
                ): LocalDateTime {
                    return try {
                        LocalDateTime.parse(input)
                    } catch (e: Exception) {
                        throw exceptionConstructor("can't parse $input", e)
                    }
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("parseValue(input, context, locale)"))
                override fun parseValue(input: Any): LocalDateTime {
                    return parse(input.toString(), ::CoercingParseValueException)
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("parseLiteral(input, context, locale)"))
                override fun parseLiteral(input: Any): LocalDateTime {
                    return when (input) {
                        is StringValue -> parse(input.value, ::CoercingParseLiteralException)
                        else -> throw CoercingParseLiteralException("can't parse $input")
                    }
                }

                @Deprecated("Deprecated in superclass", ReplaceWith("serialize(input, context, locale)"))
                override fun serialize(dataFetcherResult: Any): String {
                    return when (dataFetcherResult) {
                        is LocalDateTime -> ISO_FORMATTER.format(dataFetcherResult)
                        else -> throw CoercingSerializeException("can't serialize ${dataFetcherResult::class}")
                    }
                }
            },
        )
        .build()

/**
 * Schema generator hooks implementation that defines scalars for java.time (and threeten-extras) objects
 * and wires up the @[GraphQLAuth] directive.
 */
class LeakyCauldronHooks
    @Inject
    constructor(
        @Nullable authorizer: Authorizer<Principal>?,
        manualWiring: Map<String, KotlinSchemaDirectiveWiring>,
    ) :
    FlowSubscriptionSchemaGeneratorHooks() {
        constructor() : this(null, emptyMap())

        override val wiringFactory =
            KotlinDirectiveWiringFactory(
                mapOf(
                    "auth" to GraphQLAuthDirectiveWiring(authorizer),
                ) + manualWiring,
            )

        override fun willGenerateGraphQLType(type: KType): GraphQLType? {
            // TODO: include more from ExtendedScalars?
            return when (type.classifier) {
                Year::class -> YEAR_SCALAR
                YearMonth::class -> YEAR_MONTH_SCALAR
                YearQuarter::class -> YEAR_QUARTER_SCALAR
                LocalDateTime::class -> LOCAL_DATETIME_SCALAR
                LocalDate::class -> ExtendedScalars.Date
                LocalTime::class -> ExtendedScalars.LocalTime
                OffsetDateTime::class -> ExtendedScalars.DateTime
                UUID::class -> ExtendedScalars.UUID
                BigDecimal::class -> ExtendedScalars.GraphQLBigDecimal
                else -> null
            }
        }
    }
