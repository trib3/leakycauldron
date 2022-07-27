package com.trib3.graphql.resources

import assertk.assertThat
import assertk.assertions.endsWith
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.startsWith
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.extensions.get
import com.expediagroup.graphql.generator.toSchema
import com.expediagroup.graphql.server.operations.Query
import com.trib3.config.ConfigLoader
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.CustomDataFetcherExceptionHandler
import com.trib3.graphql.execution.RequestIdInstrumentation
import com.trib3.graphql.websocket.GraphQLContextWebSocketCreatorFactory
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.filters.CookieTokenAuthFilter
import com.trib3.testing.server.JettyWebTestContainerFactory
import com.trib3.testing.server.ResourceTestBase
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.schema.DataFetchingEnvironment
import io.dropwizard.auth.AuthDynamicFeature
import io.dropwizard.testing.common.Resource
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.eclipse.jetty.websocket.client.WebSocketUpgradeRequest
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.testng.annotations.Test
import java.net.HttpCookie
import java.security.Principal
import java.util.Optional
import java.util.concurrent.locks.ReentrantLock
import javax.ws.rs.client.Entity
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.NewCookie
import javax.ws.rs.core.Response.ResponseBuilder
import kotlin.concurrent.withLock

data class User(val name: String)

data class UserPrincipal(val user: User) : Principal {
    override fun getName(): String {
        return user.name
    }
}

class AuthTestQuery : Query {
    fun context(dfe: DataFetchingEnvironment): User? {
        return if (dfe.graphQlContext.get<Principal>() == null) {
            dfe.graphQlContext.get<ResponseBuilder>()?.cookie(NewCookie("testCookie", "testValue"))
            null
        } else {
            (dfe.graphQlContext.get<Principal>() as UserPrincipal).user
        }
    }

    fun requestContext(dfe: DataFetchingEnvironment): String? {
        val requestUri = dfe.graphQlContext.get<ContainerRequestContext>()?.uriInfo?.requestUri
        val headerValue = dfe.graphQlContext.get<ContainerRequestContext>()?.getHeaderString("other-header")
        return "$headerValue -- $requestUri"
    }
}

/**
 * Tests jersey/jetty integrations of the [GraphQLResource], such as
 * websocket upgrades and authentication
 */
class GraphQLResourceIntegrationTest : ResourceTestBase<GraphQLResource>() {
    init {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true")
    }

    override fun getResource(): GraphQLResource {
        return rawResource
    }

    override fun getContainerFactory(): TestContainerFactory {
        return JettyWebTestContainerFactory()
    }

    override fun buildAdditionalResources(resourceBuilder: Resource.Builder<*>) {
        resourceBuilder.addProvider(
            AuthDynamicFeature(
                CookieTokenAuthFilter.Builder<UserPrincipal>("authCookie")
                    .setAuthenticator {
                        if (it == "user") {
                            Optional.of(UserPrincipal(User("bill")))
                        } else {
                            Optional.empty()
                        }
                    }
                    .buildAuthFilter()
            )
        )
    }

    val graphQL = GraphQL.newGraphQL(
        toSchema(
            SchemaGeneratorConfig(
                listOf(this::class.java.packageName)
            ),
            listOf(TopLevelObject(AuthTestQuery())),
            listOf(),
            listOf()
        )
    )
        .queryExecutionStrategy(AsyncExecutionStrategy(CustomDataFetcherExceptionHandler()))
        .instrumentation(RequestIdInstrumentation())
        .build()

    val rawResource =
        GraphQLResource(
            graphQL,
            GraphQLConfig(ConfigLoader("GraphQLResourceIntegrationTest")),
            object : GraphQLContextWebSocketCreatorFactory {
                override fun getCreator(containerRequestContext: ContainerRequestContext): WebSocketCreator {
                    return WebSocketCreator { request, _ ->
                        if (request.queryString != null && request.queryString.contains("fail")) {
                            null
                        } else {
                            object : WebSocketAdapter() { // simple echoing websocket implementation
                                override fun onWebSocketText(message: String) {
                                    remote.sendString("You said $message")
                                }
                            }
                        }
                    }
                }
            },
            appConfig = TribeApplicationConfig(ConfigLoader())
        )

    @Test
    fun testWebSocketNoUpgrade() {
        val result = resource.target("/graphql")
            .request().cookie("authCookie", "user").get()
        assertThat(result.status).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED_405)
    }

    @Test
    fun testWebSocketUpgradeFail() {
        val client = WebSocketClient()
        client.start()
        try {
            val uri = resource.target("/graphql").queryParam("fail", "true").uriBuilder.scheme("ws").build()
            val adapter = WebSocketAdapter()
            assertThat {
                client.connect(
                    adapter,
                    uri,
                    ClientUpgradeRequest(
                        WebSocketUpgradeRequest(
                            client,
                            client.httpClient,
                            uri,
                            adapter
                        ).also {
                            it.cookie(HttpCookie("authCookie", "user"))
                        }
                    )
                ).get()
            }.isFailure().messageContains("Failed to upgrade")
        } finally {
            client.stop()
        }
    }

    @Test
    fun testWebSocketUpgradeUnauthenticated() {
        val result = resource.target("/graphql").queryParam("fail", "true")
            .request().header("Origin", "https://blah.com").get()
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
            val uri = resource.target("/graphql").uriBuilder.scheme("ws").build()
            val adapter = object : WebSocketAdapter() {
                override fun onWebSocketText(message: String) {
                    lock.withLock {
                        received = message
                        condition.signal()
                    }
                }
            }
            val session = client.connect(
                adapter,
                uri,
                ClientUpgradeRequest(
                    WebSocketUpgradeRequest(
                        client,
                        client.httpClient,
                        uri,
                        adapter
                    ).also {
                        it.cookie(HttpCookie("authCookie", "user"))
                    }
                )
            ).get()
            lock.withLock() {
                session.remote.sendString("Hi there")
                condition.await()
            }
            assertThat(received).isEqualTo("You said Hi there")
        } finally {
            client.stop()
        }
    }

    @Test
    fun testAuthed() {
        val result = resource.target("/graphql").request()
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
        val result = resource.target("/graphql").request()
            .post(Entity.json("""{"query":"query {context {name}}"}"""))
        assertThat(result.status).isEqualTo(HttpStatus.OK_200)
        assertThat(result.cookies["testCookie"]?.value).isEqualTo("testValue")
        val data = result.readEntity(Map::class.java)["data"] as Map<*, *>
        assertThat(data["context"]).isNull()
    }

    @Test
    fun testRequestContextUri() {
        val result = resource.target("/graphql").request().header("other-header", "other-value")
            .post(Entity.json("""{"query":"query {requestContext}"}"""))
        assertThat(result.status).isEqualTo(HttpStatus.OK_200)
        val data = result.readEntity(Map::class.java)["data"] as Map<*, *>
        assertThat(data["requestContext"]?.toString()).isNotNull().endsWith("/graphql")
        assertThat(data["requestContext"]?.toString()).isNotNull().startsWith("other-value --")
    }
}
