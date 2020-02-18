package com.trib3.graphql.resources

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.expediagroup.graphql.SchemaGeneratorConfig
import com.expediagroup.graphql.TopLevelObject
import com.expediagroup.graphql.toSchema
import com.trib3.config.ConfigLoader
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.CustomDataFetcherExceptionHandler
import com.trib3.graphql.execution.RequestIdInstrumentation
import com.trib3.testing.server.JettyWebTestContainerFactory
import com.trib3.testing.server.ResourceTestBase
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.eclipse.jetty.websocket.client.WebSocketClient
import org.eclipse.jetty.websocket.servlet.WebSocketCreator
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.testng.annotations.Test
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class GraphQLUpgradeResourceTest : ResourceTestBase<GraphQLResource>() {

    override fun getResource(): GraphQLResource {
        return rawResource
    }

    override fun getContainerFactory(): TestContainerFactory {
        return JettyWebTestContainerFactory()
    }

    val graphQL = GraphQL.newGraphQL(
        toSchema(
            SchemaGeneratorConfig(
                listOf(this::class.java.packageName)
            ),
            listOf(TopLevelObject(TestQuery())),
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
            GraphQLConfig(ConfigLoader()),
            WebSocketCreator { request, _ ->
                if (request.queryString != null && request.queryString.contains("fail")) {
                    null
                } else {
                    object : WebSocketAdapter() { // simple echoing websocket implementation
                        override fun onWebSocketText(message: String) {
                            remote.sendString("You said $message")
                        }
                    }
                }
            })

    @Test
    fun testWebSocketNoUpgrade() {
        val result = resource.target("/graphql").request().get()
        assertThat(result.status).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED_405)
    }

    @Test
    fun testWebSocketUpgradeFail() {
        val result = resource.target("/graphql").queryParam("fail", "true").request().get()
        assertThat(result.status).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED_405)
    }

    @Test
    fun testWebSocketUpgrade() {
        val client = WebSocketClient()
        client.start()
        var received: String? = null
        val lock = ReentrantLock()
        val condition = lock.newCondition()
        try {
            val session = client.connect(
                object : WebSocketAdapter() {
                    override fun onWebSocketText(message: String) {
                        lock.withLock {
                            received = message
                            condition.signal()
                        }
                    }
                },
                resource.target("/graphql").uriBuilder.scheme("ws").build()
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
}
