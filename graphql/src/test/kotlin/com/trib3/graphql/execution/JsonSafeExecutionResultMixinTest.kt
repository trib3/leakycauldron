package com.trib3.graphql.execution

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import com.fasterxml.jackson.module.kotlin.readValue
import com.trib3.json.ObjectMapperProvider
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.GraphqlErrorException
import org.testng.annotations.Test

class JsonSafeExecutionResultMixinTest {
    val mapper = ObjectMapperProvider().get().apply {
        addMixIn(ExecutionResult::class.java, JsonSafeExecutionResultMixin::class.java)
    }

    @Test
    fun testNoError() {
        val result = ExecutionResultImpl.newExecutionResult()
            .data("success")
            .addExtension("ext", "value")
            .build()
        val json = mapper.writeValueAsString(result)
        val mapped = mapper.readValue<Map<String, *>>(json)
        assertThat(mapped.keys).all {
            contains("data")
            contains("extensions")
            doesNotContain("errors")
        }
    }

    @Test
    fun testNoData() {
        val result = ExecutionResultImpl.newExecutionResult()
            .errors(
                listOf(
                    GraphqlErrorException.newErrorException().cause(IllegalStateException("bad state")).build()
                )
            )
            .addExtension("ext", "value")
            .build()
        val json = mapper.writeValueAsString(result)
        val mapped = mapper.readValue<Map<String, *>>(json)
        assertThat(mapped.keys).all {
            doesNotContain("data")
            contains("extensions")
            contains("errors")
        }
    }

    @Test
    fun testNoExt() {
        val result = ExecutionResultImpl.newExecutionResult()
            .data("success")
            .errors(
                listOf(
                    GraphqlErrorException.newErrorException().cause(IllegalStateException("bad state")).build()
                )
            )
            .build()
        val json = mapper.writeValueAsString(result)
        val mapped = mapper.readValue<Map<String, *>>(json)
        assertThat(mapped.keys).all {
            contains("data")
            doesNotContain("extensions")
            contains("errors")
        }
    }

    @Test
    fun testAll() {
        val result = ExecutionResultImpl.newExecutionResult()
            .data("success")
            .errors(
                listOf(
                    GraphqlErrorException.newErrorException().cause(IllegalStateException("bad state")).build()
                )
            )
            .addExtension("ext", "value")
            .build()
        val json = mapper.writeValueAsString(result)
        val mapped = mapper.readValue<Map<String, *>>(json)
        assertThat(mapped.keys).all {
            contains("data")
            contains("extensions")
            contains("errors")
        }
    }
}
