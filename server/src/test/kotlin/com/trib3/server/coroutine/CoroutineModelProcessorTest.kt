package com.trib3.server.coroutine

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.trib3.testing.LeakyMock
import kotlinx.coroutines.delay
import org.easymock.EasyMock
import org.glassfish.jersey.internal.inject.InjectionManager
import org.glassfish.jersey.server.AsyncContext
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.server.model.Resource
import org.glassfish.jersey.server.model.ResourceModel
import org.testng.annotations.Test
import java.util.Optional
import javax.inject.Provider
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Context
import javax.ws.rs.sse.Sse
import javax.ws.rs.sse.SseEventSink

@Path("/")
class ProcessorTestResource {
    @GET
    @Path("/simple")
    fun simpleMethod(): String {
        return "simple"
    }

    @GET
    @Path("/coroutine")
    suspend fun coroutineMethod(@QueryParam("a") a: Optional<String>): String {
        delay(1)
        return "coroutine${a.orElse("null")}"
    }

    @GET
    @Path("/sse")
    suspend fun sseMethod(
        @Context sseEventSink: SseEventSink,
        @Context sse: Sse
    ) {
        delay(1)
        sseEventSink.send(sse.newEvent("data", "value"))
        sseEventSink.close()
    }
}

class CoroutineModelProcessorTest {
    @Test
    fun testBuildResourceAndSubResources() {
        val mockInjector = LeakyMock.mock<InjectionManager>()
        val mockAsyncProvider = LeakyMock.mock<Provider<AsyncContext>>()
        EasyMock.expect(mockInjector.getInstance(ProcessorTestResource::class.java)).andReturn(ProcessorTestResource())
        EasyMock.replay(mockInjector, mockAsyncProvider)
        val resourceList = listOf(
            Resource.builder(ProcessorTestResource::class.java).build()
        )
        val processor = CoroutineModelProcessor(mockInjector, mockAsyncProvider)
        val builtResources = listOf(
            processor.processResourceModel(
                ResourceModel.Builder(resourceList, false).build(),
                ResourceConfig()
            ),
            processor.processSubResource(
                ResourceModel.Builder(resourceList, true).build(),
                ResourceConfig()
            )
        )
        builtResources.forEach { builtResource ->
            assertThat(builtResource.resources).hasSize(1)
            for (childResource in builtResource.resources[0].childResources) {
                for (method in childResource.resourceMethods) {
                    when (method.invocable.handlingMethod.name) {
                        "coroutineMethod" -> {
                            assertThat(method.invocable.parameters).hasSize(1)
                            assertThat(method.invocable.parameters[0].getAnnotation(QueryParam::class.java).value)
                                .isEqualTo("a")
                            assertThat(method.invocable.parameters[0].type.typeName)
                                .isEqualTo("java.util.Optional<java.lang.String>")
                            assertThat(method.invocable.responseType).isEqualTo(String::class.java)
                        }
                        "sseMethod" -> {
                            assertThat(method.invocable.parameters).hasSize(2)
                            assertThat(method.invocable.responseType).isEqualTo(Void.TYPE)
                        }
                        "simpleMethod" -> {
                            assertThat(method.invocable.parameters).isEmpty()
                            assertThat(method.invocable.responseType).isEqualTo(String::class.java)
                        }
                    }
                }
            }
        }
    }
}
