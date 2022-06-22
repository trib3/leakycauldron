package com.trib3.graphql.resources

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.execution.FlowSubscriptionExecutionStrategy
import com.expediagroup.graphql.generator.hooks.FlowSubscriptionSchemaGeneratorHooks
import com.expediagroup.graphql.generator.toSchema
import com.trib3.config.ConfigLoader
import com.trib3.graphql.GraphQLConfig
import com.trib3.graphql.execution.CustomDataFetcherExceptionHandler
import com.trib3.graphql.execution.RequestIdInstrumentation
import com.trib3.json.ObjectMapperProvider
import com.trib3.testing.server.JettyWebTestContainerFactory
import com.trib3.testing.server.ResourceTestBase
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.glassfish.jersey.media.sse.EventInput
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.testng.annotations.Test
import java.util.UUID
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType

class SseQuery {
    fun hello(): String {
        return "world"
    }

    fun error(): String {
        throw IllegalStateException("Forced Error")
    }
}

class SseSubscription {
    fun three(): Flow<Int> {
        return flowOf(1, 2, 3)
    }

    fun inf(): Flow<Int> {
        return flow {
            var i = 0
            while (true) {
                delay(10)
                emit(i++)
            }
        }
    }

    fun serror(): Flow<Int> {
        throw IllegalStateException("Forced Error")
    }
}

class GraphQLSseResourceIntegrationTest : ResourceTestBase<GraphQLSseResource>() {
    override fun getResource(): GraphQLSseResource {
        return rawResource
    }

    override fun getContainerFactory(): TestContainerFactory {
        return JettyWebTestContainerFactory()
    }

    private val graphQL = GraphQL.newGraphQL(
        toSchema(
            SchemaGeneratorConfig(
                listOf(this::class.java.packageName),
                hooks = FlowSubscriptionSchemaGeneratorHooks()
            ),
            listOf(TopLevelObject(SseQuery())),
            listOf(),
            listOf(TopLevelObject(SseSubscription()))
        )
    )
        .queryExecutionStrategy(AsyncExecutionStrategy(CustomDataFetcherExceptionHandler()))
        .subscriptionExecutionStrategy(FlowSubscriptionExecutionStrategy(CustomDataFetcherExceptionHandler()))
        .instrumentation(RequestIdInstrumentation())
        .build()

    private val rawResource = GraphQLSseResource(
        graphQL,
        GraphQLConfig(ConfigLoader("GraphSSEQLResourceIntegrationTest")),
        ObjectMapperProvider().get()
    )

    private val target by lazy { resource.target("/graphql/stream") }

    private fun readEventInput(eventInput: EventInput, completePayload: String): List<String> {
        val events = mutableListOf<String>()
        while (!eventInput.isClosed) {
            val event = eventInput.read()
            if (event?.name == "complete") {
                assertThat(event.readData()).isEqualTo(completePayload)
                eventInput.close()
            } else if (event?.name == "next") {
                events.add(event.readData())
            }
        }
        return events
    }

    private fun waitForStreamReady(tokenUUID: UUID) {
        // wait up to 4 seconds for the stream to be ready
        val now = System.currentTimeMillis()
        while (rawResource.activeStreams[tokenUUID] == null) {
            assertThat(System.currentTimeMillis() - now).isLessThan(4000)
            Thread.sleep(10)
        }
    }

    @Test
    fun testSseQuery() {
        val result = target.request().accept(MediaType.SERVER_SENT_EVENTS)
            .post(Entity.json("""{"query":"query {hello}"}"""), EventInput::class.java)
        val events = readEventInput(result, "")
        assertThat(events).hasSize(1)
        assertThat(events[0]).contains(""""data":{"hello":"world"}""")
    }

    @Test
    fun testSseSubscription() {
        val result = target.request().accept(MediaType.SERVER_SENT_EVENTS)
            .post(Entity.json("""{"query":"subscription {three}"}"""), EventInput::class.java)
        val events = readEventInput(result, "")
        assertThat(events).hasSize(3)
        for (i in 0..2) {
            assertThat(events[i]).contains(""""data":{"three":${i + 1}}""")
        }
    }

    @Test
    fun testSseError() {
        val result = target.request().accept(MediaType.SERVER_SENT_EVENTS)
            .post(Entity.json("""{"query":"query {error}"}"""), EventInput::class.java)
        val events = readEventInput(result, "")
        assertThat(events).hasSize(1)
        assertThat(events[0]).contains(""""message":"Forced Error"""")
        assertThat(events[0]).contains(""""data":null""")
    }

    @Test
    fun testSseSubError() {
        val result = target.request().accept(MediaType.SERVER_SENT_EVENTS)
            .post(Entity.json("""{"query":"subscription {serror}"}"""), EventInput::class.java)
        val events = readEventInput(result, "")
        assertThat(events).hasSize(1)
        assertThat(events[0]).contains(""""message":"Forced Error"""")
        assertThat(events[0]).contains(""""data":null""")
    }

    @Test
    fun testSingleConnQuery() {
        val tokenResponse = target.request().put(Entity.text(""))
        val token = tokenResponse.readEntity(String::class.java)
        assertThat(tokenResponse.status).isEqualTo(201)
        val tokenUUID = UUID.fromString(token)

        val eventInput = target.request().header("x-graphql-event-stream-token", token).get(EventInput::class.java)
        waitForStreamReady(tokenUUID)
        val query = target.request().header("x-graphql-event-stream-token", token)
            .post(Entity.json("""{"query":"query {hello}", "extensions":{"operationId":"clientspecified"}}"""))
        assertThat(query.status).isEqualTo(202)
        assertThat(rawResource.activeStreams[tokenUUID]).isNotNull()
        assertThat(rawResource.runningOperations[tokenUUID]).isNotNull()
        val query2 = target.request().header("x-graphql-event-stream-token", token)
            .post(Entity.json("""{"query":"query {hello}", "extensions":{"operationId":"clientspecified2"}}"""))
        assertThat(query2.status).isEqualTo(202)
        var completeEvents = 0
        val events = mutableListOf<String>()
        while (!eventInput.isClosed) {
            val event = eventInput.read()
            if (event?.name == "complete") {
                completeEvents++
                if (completeEvents >= 2) {
                    eventInput.close()
                }
            } else if (event?.name == "next") {
                events.add(event.readData())
            }
        }
        assertThat(events).hasSize(2)
        assertThat(events.first { it.contains("clientspecified\"") }).contains(""""data":{"hello":"world"}""")
        assertThat(events.first { it.contains("clientspecified2") }).contains(""""data":{"hello":"world"}""")
        val now = System.currentTimeMillis()
        // wait up to 4 seconds for the stream to be closed
        while (rawResource.activeStreams[tokenUUID] != null) {
            assertThat(System.currentTimeMillis() - now).isLessThan(4000)
            Thread.sleep(10)
        }
        assertThat(rawResource.activeStreams[tokenUUID]).isNull()
        assertThat(rawResource.runningOperations[tokenUUID]).isNull()
    }

    @Test
    fun testSingleConnSub() {
        val tokenResponse = target.request().put(Entity.text(""))
        val token = tokenResponse.readEntity(String::class.java)
        assertThat(tokenResponse.status).isEqualTo(201)
        val tokenUUID = UUID.fromString(token)

        val eventInput = target.queryParam("token", token).request().get(EventInput::class.java)
        waitForStreamReady(tokenUUID)
        val query = target.queryParam("token", token).request()
            .post(Entity.json("""{"query":"subscription {three}", "extensions":{"operationId":"clientspecified"}}"""))
        assertThat(query.status).isEqualTo(202)
        val events = readEventInput(eventInput, """{"id":"clientspecified"}""")
        assertThat(events).hasSize(3)
        for (i in 0..2) {
            assertThat(events[i]).contains(""""id":"clientspecified"""")
            assertThat(events[i]).contains(""""data":{"three":${i + 1}}""")
        }
    }

    @Test
    fun testSingleConnError() {
        val tokenResponse = target.request().put(Entity.text(""))
        val token = tokenResponse.readEntity(String::class.java)
        assertThat(tokenResponse.status).isEqualTo(201)
        val tokenUUID = UUID.fromString(token)

        val eventInput = target.request().header("x-graphql-event-stream-token", token).get(EventInput::class.java)
        waitForStreamReady(tokenUUID)
        val query = target.request().header("x-graphql-event-stream-token", token)
            .post(Entity.json("""{"query":"query {error}", "extensions":{"operationId":"clientspecified"}}"""))
        assertThat(query.status).isEqualTo(202)
        val events = readEventInput(eventInput, """{"id":"clientspecified"}""")
        assertThat(events).hasSize(1)
        assertThat(events[0]).contains(""""id":"clientspecified"""")
        assertThat(events[0]).contains(""""message":"Forced Error"""")
    }

    @Test
    fun testSingleConnSubError() {
        val tokenResponse = target.request().put(Entity.text(""))
        val token = tokenResponse.readEntity(String::class.java)
        assertThat(tokenResponse.status).isEqualTo(201)
        val tokenUUID = UUID.fromString(token)

        val eventInput = target.queryParam("token", token).request().get(EventInput::class.java)
        waitForStreamReady(tokenUUID)
        val query = target.queryParam("token", token).request()
            .post(Entity.json("""{"query":"subscription {serror}", "extensions":{"operationId":"clientspecified"}}"""))
        assertThat(query.status).isEqualTo(202)
        val events = readEventInput(eventInput, """{"id":"clientspecified"}""")
        assertThat(events).hasSize(1)
        assertThat(events[0]).contains(""""id":"clientspecified"""")
        assertThat(events[0]).contains(""""message":"Forced Error"""")
    }

    @Test
    fun testSingleConnCancellation() {
        val tokenResponse = target.request().put(Entity.text(""))
        val token = tokenResponse.readEntity(String::class.java)
        assertThat(tokenResponse.status).isEqualTo(201)
        val tokenUUID = UUID.fromString(token)

        val eventInput = target.request().header("x-graphql-event-stream-token", token).get(EventInput::class.java)
        waitForStreamReady(tokenUUID)
        val query = target.request().header("x-graphql-event-stream-token", token)
            .post(Entity.json("""{"query":"subscription {inf}", "extensions":{"operationId":"infinitequery"}}"""))
        assertThat(query.status).isEqualTo(202)
        while (!eventInput.isClosed) {
            val event = eventInput.read()
            if (event?.name == "next") {
                break
            }
        }
        val cancel = target.queryParam("operationId", "infinitequery")
            .request()
            .header("x-graphql-event-stream-token", token)
            .delete()
        assertThat(cancel.status).isEqualTo(204)
        val moreEvents = readEventInput(eventInput, """{"id":"infinitequery"}""")
        assertThat(moreEvents.size).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun testSingleConnEndpointsNoToken() {
        val eventInput = target.request().get(EventInput::class.java)
        val events = readEventInput(eventInput, "")
        assertThat(events).isEmpty()

        val query = target.request()
            .post(Entity.json("""{"query":"subscription {serror}", "extensions":{"operationId":"clientspecified"}}"""))
        assertThat(query.status).isEqualTo(401)
    }

    @Test
    fun testSingleConnEndpointsBadToken() {
        val eventInput = target.queryParam("token", UUID.randomUUID().toString()).request().get(EventInput::class.java)
        val events = readEventInput(eventInput, "")
        assertThat(events).isEmpty()

        val query = target.queryParam("token", UUID.randomUUID().toString()).request()
            .post(Entity.json("""{"query":"subscription {serror}", "extensions":{"operationId":"clientspecified"}}"""))
        assertThat(query.status).isEqualTo(401)
    }

    @Test
    fun testSingleConnBadCancellation() = runBlocking {
        val cancelNoToken = target.queryParam("operationId", "badValue")
            .request()
            .delete()
        assertThat(cancelNoToken.status).isEqualTo(401)

        val cancelBadTokenHeader = target.queryParam("operationId", "badValue")
            .request()
            .header("x-graphql-event-stream-token", UUID.randomUUID())
            .delete()
        assertThat(cancelBadTokenHeader.status).isEqualTo(204)

        val cancelBadTokenQueryParam = target.queryParam("operationId", "badValue")
            .queryParam("token", UUID.randomUUID())
            .request()
            .delete()
        assertThat(cancelBadTokenQueryParam.status).isEqualTo(204)

        val tokenResponse = target.request().put(Entity.text(""))
        val token = tokenResponse.readEntity(String::class.java)
        val tokenUUID = UUID.fromString(token)
        assertThat(tokenResponse.status).isEqualTo(201)

        val eventInput = target.request().header("x-graphql-event-stream-token", token).get(EventInput::class.java)
        waitForStreamReady(tokenUUID)
        val query = target.request().header("x-graphql-event-stream-token", token)
            .post(Entity.json("""{"query":"query {hello}", "extensions":{"operationId":"clientspecified"}}"""))
        assertThat(query.status).isEqualTo(202)
        val cancelGoodTokenBadOperationId = target.queryParam("operationId", "badValue")
            .queryParam("token", token)
            .request()
            .delete()
        assertThat(cancelGoodTokenBadOperationId.status).isEqualTo(204)
        eventInput.close()
    }
}
