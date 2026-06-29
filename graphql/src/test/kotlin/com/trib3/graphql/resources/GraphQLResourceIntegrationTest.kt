package com.trib3.graphql.resources

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.endsWith
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.startsWith
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.extensions.get
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.operations.Query
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.config.ConfigLoader
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.CustomDataFetcherExceptionHandler
import com.trib3.graphql.execution.RequestIdInstrumentation
import com.trib3.graphql.modules.DefaultGraphQLModule
import com.trib3.json.ObjectMapperProvider
import com.trib3.json.ObjectMapperProvider.Companion.OBJECT_MAPPER_MIXINS
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.filters.CookieTokenAuthFilter
import com.trib3.server.swagger.SwaggerInitializer
import com.trib3.testing.server.JettyWebTestContainerFactory
import com.trib3.testing.server.ResourceTestBase
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.schema.DataFetchingEnvironment
import io.dropwizard.auth.AuthDynamicFeature
import io.dropwizard.testing.common.Resource
import io.swagger.v3.oas.integration.OpenApiContextLocator
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.NewCookie
import jakarta.ws.rs.core.Response.ResponseBuilder
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.eclipse.jetty.websocket.core.server.WebSocketCreator
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.testng.annotations.Guice
import org.testng.annotations.Test
import java.net.HttpCookie
import java.security.Principal
import java.util.Optional
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

data class User(
    val name: String,
)

data class UserPrincipal(
    val user: User,
) : Principal {
    override fun getName(): String = user.name
}

class AuthTestQuery : Query {
    fun context(dfe: DataFetchingEnvironment): User? =
        if (dfe.graphQlContext.get<Principal>() == null) {
            dfe.graphQlContext.get<ResponseBuilder>()?.cookie(
                NewCookie.Builder("testCookie").value("testValue").build(),
            )
            null
        } else {
            (dfe.graphQlContext.get<Principal>() as UserPrincipal).user
        }

    fun requestContext(dfe: DataFetchingEnvironment): String? {
        val requestUri =
            dfe.graphQlContext
                .get<ContainerRequestContext>()
                ?.uriInfo
                ?.requestUri
        val headerValue = dfe.graphQlContext.get<ContainerRequestContext>()?.getHeaderString("other-header")
        return "$headerValue -- $requestUri"
    }
}

/**
 * Tests jersey/jetty integrations of the [GraphQLResource], such as
 * websocket upgrades and authentication
 */
@Guice(modules = [DefaultGraphQLModule::class])
class GraphQLResourceIntegrationTest
    @Inject
    constructor(
        @Named(OBJECT_MAPPER_MIXINS)
        private val mixins: Map<KClass<*>, KClass<*>>,
    ) : ResourceTestBase<GraphQLResource>() {
        init {
            System.setProperty("sun.net.http.allowRestrictedHeaders", "true")
        }

        override fun getResource(): GraphQLResource = rawResource

        override fun getContainerFactory(): TestContainerFactory = JettyWebTestContainerFactory()

        override fun getObjectMapper(): ObjectMapper =
            ObjectMapperProvider(
                mixins,
                null,
            ).get()

        override fun buildAdditionalResources(resourceBuilder: Resource.Builder<*>) {
            resourceBuilder.addProvider(
                AuthDynamicFeature(
                    CookieTokenAuthFilter
                        .Builder<UserPrincipal>("authCookie")
                        .setAuthenticator {
                            if (it == "user") {
                                Optional.of(UserPrincipal(User("bill")))
                            } else {
                                Optional.empty()
                            }
                        }.buildAuthFilter(),
                ),
            )
        }

        val graphQL =
            GraphQL
                .newGraphQL(
                    toSchema(
                        SchemaGeneratorConfig(
                            listOf(this::class.java.packageName),
                        ),
                        listOf(TopLevelObject(AuthTestQuery())),
                        listOf(),
                        listOf(),
                    ),
                ).queryExecutionStrategy(AsyncExecutionStrategy(CustomDataFetcherExceptionHandler()))
                .instrumentation(RequestIdInstrumentation())
                .build()

        val rawResource =
            GraphQLResource(
                graphQL,
                GraphQLConfig(ConfigLoader("GraphQLResourceIntegrationTest")),
                appConfig = TribeApplicationConfig(ConfigLoader("GraphQLResourceIntegrationTest")),
                creator =
                    WebSocketCreator { request, _, callback ->
                        if (request.httpURI.query != null && request.httpURI.query.contains("fail")) {
                            callback.failed(IllegalStateException("Forced failure"))
                            null
                        } else {
                            object : Session.Listener.AutoDemanding { // simple echoing websocket implementation
                                var session: Session? = null

                                override fun onWebSocketOpen(session: Session?) {
                                    this.session = session
                                }

                                override fun onWebSocketText(message: String) {
                                    session?.sendText("You said $message", null)
                                }
                            }
                        }
                    },
            )

        @Test
        fun testWebSocketNoUpgrade() {
            val result =
                resource
                    .target("/graphql")
                    .request()
                    .cookie("authCookie", "user")
                    .get()
            assertThat(result.status).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED_405)
        }

        @Test
        fun testWebSocketUpgradeFail() {
            val client = WebSocketClient()
            client.start()
            try {
                val uri =
                    resource
                        .target("/graphql")
                        .queryParam("fail", "true")
                        .uriBuilder
                        .scheme("ws")
                        .build()
                val adapter =
                    object : Session.Listener.AutoDemanding {
                    }
                assertFailure {
                    client
                        .connect(
                            adapter,
                            ClientUpgradeRequest(uri).also {
                                it.cookies = listOf(HttpCookie("authCookie", "user"))
                            },
                        ).get()
                }.messageContains("Failed to upgrade")
            } finally {
                client.stop()
            }
        }

        @Test
        fun testWebSocketUpgradeUnauthenticated() {
            val result =
                resource
                    .target("/graphql")
                    .queryParam("fail", "true")
                    .request()
                    .header("Origin", "https://blah.com")
                    .get()
            assertThat(result.status).isEqualTo(HttpStatus.UNAUTHORIZED_401)
        }

        @Test
        fun testWebSocketUpgrade() {
            val client = WebSocketClient()
            client.start()
            var received: String? = null
            val lock = ReentrantLock()
            val condition = lock.newCondition()
            try {
                val uri =
                    resource
                        .target("/graphql")
                        .uriBuilder
                        .scheme("ws")
                        .build()
                val adapter =
                    object : Session.Listener.AutoDemanding {
                        override fun onWebSocketText(message: String) {
                            lock.withLock {
                                received = message
                                condition.signal()
                            }
                        }
                    }
                val session =
                    client
                        .connect(
                            adapter,
                            ClientUpgradeRequest(uri).also {
                                it.cookies = listOf(HttpCookie("authCookie", "user"))
                            },
                        ).get()
                lock.withLock {
                    session.sendText("Hi there", null)
                    condition.await()
                }
                assertThat(received).isEqualTo("You said Hi there")
            } finally {
                client.stop()
            }
        }

        @Test
        fun testAuthed() {
            val result =
                resource
                    .target("/graphql")
                    .request()
                    .cookie("authCookie", "user")
                    .post(Entity.json("""{"query":"query {context {name}}"}"""))
            assertThat(result.status).isEqualTo(HttpStatus.OK_200)
            assertThat(result.cookies["testCookie"]).isNull()
            val data = result.readEntity(Map::class.java)["data"] as Map<*, *>
            val context = data["context"] as Map<*, *>
            assertThat(context["name"]).isEqualTo("bill")
        }

        @Test
        fun testUnAuthed() {
            val result =
                resource
                    .target("/graphql")
                    .request()
                    .post(Entity.json("""{"query":"query {context {name}}"}"""))
            assertThat(result.status).isEqualTo(HttpStatus.OK_200)
            assertThat(result.cookies["testCookie"]?.value).isEqualTo("testValue")
            val data = result.readEntity(Map::class.java)["data"] as Map<*, *>
            assertThat(data["context"]).isNull()
        }

        @Test
        fun testRequestContextUri() {
            val result =
                resource
                    .target("/graphql")
                    .request()
                    .header("other-header", "other-value")
                    .post(Entity.json("""{"query":"query {requestContext}"}"""))
            assertThat(result.status).isEqualTo(HttpStatus.OK_200)
            val data = result.readEntity(Map::class.java)["data"] as Map<*, *>
            assertThat(data["requestContext"]?.toString()).isNotNull().endsWith("/graphql")
            assertThat(data["requestContext"]?.toString()).isNotNull().startsWith("other-value --")
        }

        @Test
        fun testSwaggerIntegration() {
            val initializer =
                SwaggerInitializer(
                    "GraphQLSwagger",
                    TribeApplicationConfig(ConfigLoader()),
                    ObjectMapperProvider().get(),
                )
            initializer.process(
                object : Application() {
                    override fun getClasses(): Set<Class<*>> =
                        setOf(GraphQLResource::class.java, GraphQLSseResource::class.java)
                },
            )
            val context = OpenApiContextLocator.getInstance().getOpenApiContext("GraphQLSwagger")
            val openApi = context.read()
            assertThat(openApi.paths["/graphql"]).isNotNull()
            assertThat(openApi.paths["/graphql/stream"]).isNotNull()

            // POST /graphql request body should default to application/json (not */*)
            // and `$ref` the sealed-parent component (not inline its oneOf at the use site)
            val postBodyContent =
                openApi.paths["/graphql"]
                    ?.post
                    ?.requestBody
                    ?.content
            assertThat(postBodyContent?.keys).isEqualTo(setOf("application/json"))
            val postBodySchema = postBodyContent?.get("application/json")?.schema
            assertThat(postBodySchema?.`$ref`).isEqualTo("#/components/schemas/GraphQLServerRequest")

            val schemas = openApi.components.schemas
            // Sealed parent: oneOf each subclass.
            val gqlServerRequest = schemas["GraphQLServerRequest"]
            assertThat(gqlServerRequest).isNotNull()
            assertThat(gqlServerRequest!!.oneOf?.map { it.`$ref` })
                .isNotNull()
                .containsExactlyInAnyOrder(
                    "#/components/schemas/GraphQLRequest",
                    "#/components/schemas/GraphQLBatchRequest",
                )

            // Single-request subclass: normal object with properties.
            val gqlRequest = schemas["GraphQLRequest"]
            assertThat(gqlRequest).isNotNull()
            assertThat(gqlRequest!!.type).isEqualTo("object")
            assertThat(gqlRequest.properties.keys).contains("query")

            // Batch subclass: array of GraphQLRequest.
            val gqlBatch = schemas["GraphQLBatchRequest"]
            assertThat(gqlBatch).isNotNull()
            assertThat(gqlBatch!!.type).isEqualTo("array")
            assertThat(gqlBatch.items?.`$ref`).isEqualTo("#/components/schemas/GraphQLRequest")
        }
    }
