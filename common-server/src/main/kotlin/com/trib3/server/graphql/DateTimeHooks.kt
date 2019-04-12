package com.trib3.server.graphql

import com.expedia.graphql.hooks.SchemaGeneratorHooks
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLType
import org.threeten.extra.YearQuarter
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

/**
 * Schema generator hooks implementation that defines scalars for java.time (and threeten-extras) objects
 */
class DateTimeHooks : SchemaGeneratorHooks {
    override fun willGenerateGraphQLType(type: KType): GraphQLType? {
        return when (type.classifier) {
            YearMonth::class -> YEAR_MONTH_SCALAR
            YearQuarter::class -> YEAR_QUARTER_SCALAR
            else -> null
        }
    }
}
