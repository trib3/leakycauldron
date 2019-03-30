package com.trib3.server.resources

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasRootCause
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.message
import assertk.assertions.prop
import com.coxautodev.graphql.tools.GraphQLQueryResolver
import com.fasterxml.jackson.module.kotlin.readValue
import com.trib3.json.ObjectMapperProvider
import graphql.ExecutionResult
import graphql.GraphQLError
import org.testng.annotations.Test

class TestQuery : GraphQLQueryResolver {
    fun test(value: String): String {
        return value
    }

    fun error(): String {
        throw IllegalArgumentException("an error was thrown")
    }

    fun unknownError(): String {
        throw IllegalArgumentException()
    }
}

class GraphqlResourceTest {
    val resource = GraphqlResource(setOf(this::class.java.packageName), setOf(TestQuery()), setOf())
    val objectMapper = ObjectMapperProvider().get()
    @Test
    fun testNotConfigured() {
        val notConfiguredResource = GraphqlResource(setOf(), setOf(), setOf())
        assertThat {
            notConfiguredResource.graphQl(GraphRequest("", null, null))
        }.thrownError {
            message().isNotNull().contains("not configured")
        }
    }

    @Test
    fun testSimpleQuery() {
        val result = resource.graphQl(GraphRequest("query {test(value:\"123\")}", null, null))
        val graphqlResult = result.entity as ExecutionResult
        assertThat(graphqlResult.getData<Map<String, String>>()["test"]).isEqualTo("123")
    }

    @Test
    fun testVariablesQuery() {
        val result = resource.graphQl(
            GraphRequest(
                "query(${'$'}val:String!) {test(value:${'$'}val)}",
                mapOf("val" to "123"),
                null
            )
        )
        val graphqlResult = result.entity as ExecutionResult
        assertThat(graphqlResult.getData<Map<String, String>>()["test"]).isEqualTo("123")
    }

    @Test
    fun testErrorQuery() {
        val result = resource.graphQl(GraphRequest("query {error}", mapOf(), null))
        val graphqlResult = result.entity as ExecutionResult
        assertThat(graphqlResult.errors.first()).prop(GraphQLError::getMessage).isEqualTo("an error was thrown")
        assertThat(graphqlResult.errors.first()).isInstanceOf(SanitizedGraphQLError::class)
        assertThat((graphqlResult.errors.first() as SanitizedGraphQLError).exception)
            .hasRootCause(IllegalArgumentException("an error was thrown"))
        val serializedError = objectMapper.writeValueAsString(graphqlResult.errors.first())
        assertThat(objectMapper.readValue<Map<String, *>>(serializedError).keys).doesNotContain("exception")
    }

    @Test
    fun testUnknownErrorQuery() {
        val result = resource.graphQl(GraphRequest("query {unknownError}", mapOf(), null))
        val graphqlResult = result.entity as ExecutionResult
        assertThat(graphqlResult.errors.first()).prop(GraphQLError::getMessage)
            .contains("Exception while fetching data")
        val serializedError = objectMapper.writeValueAsString(graphqlResult.errors.first())
        assertThat(objectMapper.readValue<Map<String, *>>(serializedError).keys).doesNotContain("exception")
    }
}
