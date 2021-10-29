package com.trib3.graphql.execution

import com.expediagroup.graphql.generator.annotations.GraphQLDirective
import com.expediagroup.graphql.generator.directives.KotlinFieldDirectiveEnvironment
import com.expediagroup.graphql.generator.directives.KotlinSchemaDirectiveWiring
import com.trib3.graphql.resources.getInstance
import graphql.introspection.Introspection
import graphql.schema.DataFetcher
import graphql.schema.GraphQLFieldDefinition
import io.dropwizard.auth.Authorizer
import java.security.Principal
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response

/**
 * Annotation to be used on field definitions to
 * restrict access based on the context [Principal]'s role.
 * If no roles are specified, any authenticated [Principal]
 * will be authorized to access the annotated field.
 *
 * Note that such annotated field elements should be nullable,
 * as failure to auth will result in a null return data value!
 */
@GraphQLDirective(
    name = "auth",
    description = "only allows given roles to access the annotated element",
    locations = [Introspection.DirectiveLocation.FIELD_DEFINITION]
)
annotation class GraphQLAuth(val roles: Array<String> = [])

/**
 * Directive wiring that checks for auth before fetching data.
 *
 * If no roles or no authorizer are specified, will fetch data as long as a [Principal] is authenticated.
 * Otherwise will only fetch data if the authenticated [Principal] is assigned any listed role.
 *
 * If data is not fetched, then [Response.Status.UNAUTHORIZED] will be returned as an error for an
 * authenticated but unauthorized user, and [Response.Status.FORBIDDEN] will be returned as an error
 * for an unauthenticated user.
 */
class GraphQLAuthDirectiveWiring(private val authorizer: Authorizer<Principal>?) : KotlinSchemaDirectiveWiring {

    private fun missingAllowedRole(principal: Principal, roles: Array<String>): Boolean {
        return authorizer != null && roles.isNotEmpty() && roles.none {
            authorizer.authorize(principal, it, null)
        }
    }

    override fun onField(environment: KotlinFieldDirectiveEnvironment): GraphQLFieldDefinition {
        @Suppress("UNCHECKED_CAST")
        val roles = environment.directive.getArgument("roles").argumentValue.value as Array<String>
        val originalDataFetcher = environment.getDataFetcher()

        val authFetcher = DataFetcher { dfe ->
            val principal = dfe.graphQlContext.getInstance<Principal>()
            if (principal == null || missingAllowedRole(principal, roles)) {
                val status = if (principal == null) {
                    Response.Status.UNAUTHORIZED
                } else {
                    Response.Status.FORBIDDEN
                }
                throw WebApplicationException(status.statusCode)
            } else {
                originalDataFetcher.get(dfe)
            }
        }
        environment.setDataFetcher(authFetcher)
        return super.onField(environment)
    }
}
