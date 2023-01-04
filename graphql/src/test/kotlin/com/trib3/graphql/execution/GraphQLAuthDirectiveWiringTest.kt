package com.trib3.graphql.execution

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.extensions.get
import com.expediagroup.graphql.generator.toSchema
import com.trib3.graphql.resources.getGraphQLContextMap
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.DataFetchingEnvironment
import io.dropwizard.auth.Authorizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.testng.annotations.Test
import java.security.Principal

class AuthQuery {
    @GraphQLAuth
    fun needUser(dfe: DataFetchingEnvironment): String? {
        return dfe.graphQlContext.get<Principal>()?.name
    }

    fun noUser(): String {
        return "nope"
    }

    @GraphQLAuth(["ADMIN"])
    fun needSuperUser(dfe: DataFetchingEnvironment): String? {
        return dfe.graphQlContext.get<Principal>()?.name
    }
}

class TestAuthorizer : Authorizer<Principal> {
    override fun authorize(principal: Principal, role: String): Boolean {
        return role != "ADMIN" || principal.name == "ADMIN"
    }
}

class GraphQLGraphQLAuthDirectiveWiringTest {
    val scope = CoroutineScope(Dispatchers.Default)
    val regularUser = Principal { "bill" }
    val superUser = Principal { "ADMIN" }

    fun query(principal: Principal?, authorizer: Authorizer<Principal>? = TestAuthorizer()): ExecutionResult {
        val hooks = LeakyCauldronHooks(authorizer, emptyMap())
        val config =
            SchemaGeneratorConfig(
                listOf(this::class.java.packageName),
                hooks = hooks,
            )
        return GraphQL.newGraphQL(
            toSchema(config, listOf(TopLevelObject(AuthQuery()))),
        ).build().execute(
            ExecutionInput.newExecutionInput()
                .query("{ needUser noUser needSuperUser }")
                .graphQLContext(
                    getGraphQLContextMap(scope, principal),
                )
                .build(),
        )
    }

    @Test
    fun testRegularUser() {
        val result = query(regularUser)
        val data = result.getData<Map<String, String>>()
        val errors = result.errors
        assertThat(data["needUser"]).isEqualTo(regularUser.name)
        assertThat(data["noUser"]).isEqualTo("nope")
        assertThat(data["needSuperUser"]).isNull()
        assertThat(errors).hasSize(1)
        assertThat(errors[0].path[0]).isEqualTo("needSuperUser")
        assertThat(errors[0].message).isNotNull().contains("HTTP 403 Forbidden")
    }

    @Test
    fun testSuperUser() {
        val result = query(superUser)
        val data = result.getData<Map<String, String>>()
        val errors = result.errors
        assertThat(data["needUser"]).isEqualTo(superUser.name)
        assertThat(data["noUser"]).isEqualTo("nope")
        assertThat(data["needSuperUser"]).isEqualTo(superUser.name)
        assertThat(errors).isEmpty()
    }

    @Test
    fun testNoUser() {
        val result = query(null)
        val data = result.getData<Map<String, String>>()
        val errors = result.errors
        assertThat(data["needUser"]).isNull()
        assertThat(data["noUser"]).isEqualTo("nope")
        assertThat(data["needSuperUser"]).isNull()
        assertThat(errors).hasSize(2)
        assertThat(errors[0].path[0]).isEqualTo("needUser")
        assertThat(errors[0].message).isNotNull().contains("HTTP 401 Unauthorized")
        assertThat(errors[1].path[0]).isEqualTo("needSuperUser")
        assertThat(errors[1].message).isNotNull().contains("HTTP 401 Unauthorized")
    }

    @Test
    fun testRegularUserNoAuthorizer() {
        val result = query(regularUser, null)
        val data = result.getData<Map<String, String>>()
        val errors = result.errors
        assertThat(data["needUser"]).isEqualTo(regularUser.name)
        assertThat(data["noUser"]).isEqualTo("nope")
        assertThat(data["needSuperUser"]).isEqualTo(regularUser.name)
        assertThat(errors).isEmpty()
    }

    @Test
    fun testSuperUserNoAuthorizer() {
        val result = query(superUser, null)
        val data = result.getData<Map<String, String>>()
        val errors = result.errors
        assertThat(data["needUser"]).isEqualTo(superUser.name)
        assertThat(data["noUser"]).isEqualTo("nope")
        assertThat(data["needSuperUser"]).isEqualTo(superUser.name)
        assertThat(errors).isEmpty()
    }

    @Test
    fun testNoUserNoAuthorizer() {
        val result = query(null, null)
        val data = result.getData<Map<String, String>>()
        val errors = result.errors
        assertThat(data["needUser"]).isNull()
        assertThat(data["noUser"]).isEqualTo("nope")
        assertThat(data["needSuperUser"]).isNull()
        assertThat(errors).hasSize(2)
        assertThat(errors[0].path[0]).isEqualTo("needUser")
        assertThat(errors[0].message).isNotNull().contains("HTTP 401 Unauthorized")
        assertThat(errors[1].path[0]).isEqualTo("needSuperUser")
        assertThat(errors[1].message).isNotNull().contains("HTTP 401 Unauthorized")
    }

    @Test
    fun testNoContext() {
        val hooks = LeakyCauldronHooks(null, emptyMap())
        val config =
            SchemaGeneratorConfig(
                listOf(this::class.java.packageName),
                hooks = hooks,
            )
        val result = GraphQL.newGraphQL(
            toSchema(config, listOf(TopLevelObject(AuthQuery()))),
        ).build().execute(
            ExecutionInput.newExecutionInput()
                .query("{ needUser noUser needSuperUser }")
                .build(),
        )
        val data = result.getData<Map<String, String>>()
        val errors = result.errors
        assertThat(data["needUser"]).isNull()
        assertThat(data["noUser"]).isEqualTo("nope")
        assertThat(data["needSuperUser"]).isNull()
        assertThat(errors).hasSize(2)
        assertThat(errors[0].path[0]).isEqualTo("needUser")
        assertThat(errors[0].message).isNotNull().contains("HTTP 401 Unauthorized")
        assertThat(errors[1].path[0]).isEqualTo("needSuperUser")
        assertThat(errors[1].message).isNotNull().contains("HTTP 401 Unauthorized")
    }
}
