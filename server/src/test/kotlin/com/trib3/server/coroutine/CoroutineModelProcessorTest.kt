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
}

class CoroutineModelProcessorTest {
    @Test
    fun testBuildResource() {
        val mockInjector = LeakyMock.mock<InjectionManager>()
        val mockAsyncProvider = LeakyMock.mock<Provider<AsyncContext>>()
        EasyMock.expect(mockInjector.getInstance(ProcessorTestResource::class.java)).andReturn(ProcessorTestResource())
        EasyMock.replay(mockInjector, mockAsyncProvider)
        val resourceModel = ResourceModel.Builder(
            listOf(
                Resource.builder(ProcessorTestResource::class.java).build()
            ),
            false
        ).build()
        val builtResource =
            CoroutineModelProcessor(mockInjector, mockAsyncProvider).processResourceModel(
                resourceModel,
                ResourceConfig()
            )
        assertThat(builtResource.resources).hasSize(1)
        for (childResource in builtResource.resources[0].childResources) {
            for (method in childResource.resourceMethods) {
                if (method.invocable.handlingMethod.name == "coroutineMethod") {
                    assertThat(method.invocable.parameters).hasSize(1)
                    assertThat(method.invocable.parameters[0].getAnnotation(QueryParam::class.java).value)
                        .isEqualTo("a")
                    assertThat(method.invocable.parameters[0].type.typeName)
                        .isEqualTo("java.util.Optional<java.lang.String>")
                } else {
                    assertThat(method.invocable.parameters).isEmpty()
                }
            }
        }
    }

    @Test
    fun testBuildSubResource() {
        val mockInjector = LeakyMock.mock<InjectionManager>()
        val mockAsyncProvider = LeakyMock.mock<Provider<AsyncContext>>()
        EasyMock.expect(mockInjector.getInstance(ProcessorTestResource::class.java)).andReturn(ProcessorTestResource())
        EasyMock.replay(mockInjector, mockAsyncProvider)
        val resourceModel = ResourceModel.Builder(
            listOf(
                Resource.builder(ProcessorTestResource::class.java).build()
            ),
            true
        ).build()
        val builtResource =
            CoroutineModelProcessor(mockInjector, mockAsyncProvider).processSubResource(
                resourceModel,
                ResourceConfig()
            )
        assertThat(builtResource.resources).hasSize(1)
        for (childResource in builtResource.resources[0].childResources) {
            for (method in childResource.resourceMethods) {
                if (method.invocable.handlingMethod.name == "coroutineMethod") {
                    assertThat(method.invocable.parameters).hasSize(1)
                    assertThat(method.invocable.parameters[0].getAnnotation(QueryParam::class.java).value)
                        .isEqualTo("a")
                    assertThat(method.invocable.parameters[0].type.typeName)
                        .isEqualTo("java.util.Optional<java.lang.String>")
                } else {
                    assertThat(method.invocable.parameters).isEmpty()
                }
            }
        }
    }
}
