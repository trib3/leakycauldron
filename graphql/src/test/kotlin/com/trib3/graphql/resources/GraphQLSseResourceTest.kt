package com.trib3.graphql.resources

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isSuccess
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.trib3.config.ConfigLoader
import com.trib3.graphql.GraphQLConfig
import com.trib3.json.ObjectMapperProvider
import com.trib3.server.filters.RequestIdFilter
import com.trib3.testing.LeakyMock
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.GraphQL
import jakarta.ws.rs.sse.OutboundSseEvent
import jakarta.ws.rs.sse.Sse
import jakarta.ws.rs.sse.SseEventSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.easymock.CaptureType
import org.easymock.EasyMock
import org.glassfish.jersey.media.sse.OutboundEvent
import org.testng.annotations.Test
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Most functional tests of SSE handling are in [GraphQLSseResourceIntegrationTest], but some
 * corner cases are easier to trigger with unit tests/mocks
 */
class GraphQLSseResourceTest {

    private val resource: GraphQLSseResource
    private val principal = UserPrincipal(User("bill"))

    init {
        val mockFlow = flow<ExecutionResult> {
            throw IllegalArgumentException("fake error")
        }
        val graphQL = LeakyMock.mock<GraphQL>()
        val executionResult = ExecutionResultImpl.newExecutionResult().data(mockFlow).build()

        EasyMock.expect(graphQL.executeAsync(EasyMock.anyObject<ExecutionInput>()))
            .andReturn(CompletableFuture.completedFuture(executionResult)).anyTimes()

        EasyMock.replay(graphQL)

        resource = GraphQLSseResource(
            graphQL,
            GraphQLConfig(ConfigLoader("GraphQLSseResourceTest")),
            ObjectMapperProvider().get(),
        )
    }

    @Test
    fun testFlowError() = runBlocking {
        val eventCapture = EasyMock.newCapture<OutboundSseEvent>(CaptureType.ALL)
        val mockSink = LeakyMock.mock<SseEventSink>()
        EasyMock.expect(mockSink.send(EasyMock.capture(eventCapture))).andReturn(null).anyTimes()
        EasyMock.expect(mockSink.close())
        val mockSse = LeakyMock.mock<Sse>()
        EasyMock.expect(mockSse.newEventBuilder()).andReturn(OutboundEvent.Builder()).anyTimes()
        EasyMock.replay(mockSink, mockSse)
        assertThat {
            resource.querySse(
                mockSink,
                mockSse,
                Optional.of(principal),
                GraphQLRequest("query"),
            )
        }.isSuccess()
        assertThat(eventCapture.values[0].name).isEqualTo("next")
        assertThat(eventCapture.values[0].data.toString()).contains(""""message":"fake error"""")
        assertThat(eventCapture.values[1].name).isEqualTo("complete")
        assertThat(eventCapture.values[1].data.toString()).isEqualTo("")
    }

    @Test
    fun testFlowErrorSingleConn() = runBlocking {
        val eventCapture = EasyMock.newCapture<OutboundSseEvent>(CaptureType.ALL)
        val mockSink = LeakyMock.mock<SseEventSink>()
        EasyMock.expect(mockSink.send(EasyMock.capture(eventCapture))).andReturn(null).anyTimes()
        EasyMock.expect(mockSink.close())
        val mockSse = LeakyMock.mock<Sse>()
        EasyMock.expect(mockSse.newEventBuilder()).andReturn(OutboundEvent.Builder()).anyTimes()
        EasyMock.replay(mockSink, mockSse)
        val token = UUID.fromString(resource.reserveEventStream(Optional.of(principal)).entity.toString())
        val stream = launch(Dispatchers.IO) {
            resource.eventStream(mockSink, mockSse, token, null)
        }
        // wait for stream to fully open
        while (resource.activeStreams[token] == null) {
            delay(10)
        }
        RequestIdFilter.withRequestId("querytofail") {
            resource.queryOpenStream(
                Optional.empty(),
                GraphQLRequest("query"),
                token,
                null,
            )
            // wait for stream events
            while (eventCapture.values.size < 2) {
                delay(10)
            }
            stream.cancel()
            val events = eventCapture.values
            assertThat(events[0].name).isEqualTo("next")
            assertThat(events[0].data.toString()).contains(""""message":"fake error"""")
            assertThat(events[0].data.toString()).contains(""""id":"querytofail"""")
            assertThat(events[1].name).isEqualTo("complete")
            assertThat(events[1].data.toString()).isEqualTo("""{"id":"querytofail"}""")
        }
    }

    @Test
    fun testReservationAuth() {
        assertThat(resource.reserveEventStream(Optional.empty()).status).isEqualTo(401)
        assertThat(resource.reserveEventStream(Optional.of(principal)).status).isEqualTo(201)
    }
}
