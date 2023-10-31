package com.trib3.graphql.modules

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.size
import com.expediagroup.graphql.dataloader.KotlinDataLoader
import com.expediagroup.graphql.dataloader.KotlinDataLoaderRegistryFactory
import com.expediagroup.graphql.generator.extensions.get
import com.expediagroup.graphql.server.extensions.toExecutionInput
import com.expediagroup.graphql.server.types.GraphQLRequest
import com.trib3.config.modules.KMSModule
import com.trib3.graphql.resources.GraphQLResource
import com.trib3.server.filters.RequestIdFilter
import com.trib3.server.modules.EnvironmentCallback
import com.trib3.server.modules.TribeApplicationModule
import com.trib3.testing.LeakyMock
import graphql.GraphQL
import graphql.GraphQLContext
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions
import io.dropwizard.core.setup.Environment
import io.dropwizard.jetty.MutableServletContextHandler
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.easymock.EasyMock
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import org.testng.annotations.Guice
import org.testng.annotations.Test
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Named
import javax.servlet.ServletContainerInitializer

class DummyQuery {
    fun query(): String {
        return "test"
    }
}

class DummyModule : GraphQLApplicationModule() {
    override fun configureApplication() {
        graphQLQueriesBinder().addBinding().to<DummyQuery>()
        graphQLInstrumentationsBinder().addBinding().toInstance(
            DataLoaderDispatcherInstrumentation(
                DataLoaderDispatcherInstrumentationOptions.newOptions().includeStatistics(true),
            ),
        )
    }
}

@Guice(modules = [DefaultGraphQLModule::class, KMSModule::class, DummyModule::class])
class GraphQLApplicationModuleTest
@Inject constructor(
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    val resources: Set<Any>,
    val graphQL: GraphQL,
    val environmentCallbacks: Set<EnvironmentCallback>,
) {
    @Test
    fun testBinding() {
        val graphQLResources = resources.filterIsInstance<GraphQLResource>()
        assertThat(graphQLResources).size().isGreaterThan(0)
        assertThat(graphQLResources.first().dataLoaderRegistryFactory).isNull()
        assertThat(environmentCallbacks).isNotEmpty()
        RequestIdFilter.withRequestId("graphQLInstrumentationBindingTest") {
            val result = graphQL.execute("query test")
            assertThat(result.extensions["RequestId"]).isEqualTo("graphQLInstrumentationBindingTest")
            assertThat(result.extensions["dataloader"]).isNotNull()
            val x = result.extensions["dataloader"] as Map<*, *>
            assertThat(x["overall-statistics"]).isNotNull()
            assertThat(x["individual-statistics"]).isNotNull()
        }
    }

    @Test
    fun testGraphQLProvider() {
        val module = DefaultGraphQLModule()
        val graphQLInstance = module.provideGraphQLInstance(
            setOf(),
            setOf(DummyQuery()),
            setOf(),
            setOf(),
            setOf(),
        )
        assertThat(graphQLInstance).isNotNull()
    }

    @Test
    fun testJettyWebSocketInitializer() {
        val environment = LeakyMock.mock<Environment>()
        val mockHandler = LeakyMock.mock<MutableServletContextHandler>()
        val initializerCapture = EasyMock.newCapture<ServletContainerInitializer>()
        EasyMock.expect(environment.applicationContext).andReturn(mockHandler)
        EasyMock.expect(mockHandler.addServletContainerInitializer(LeakyMock.capture(initializerCapture)))
            .andReturn(null)
        EasyMock.expect(mockHandler.isStopped).andReturn(true)
        EasyMock.replay(environment, mockHandler)
        environmentCallbacks.forEach {
            it.invoke(environment)
        }
        assertThat(initializerCapture.value).isInstanceOf(JettyWebSocketServletContainerInitializer::class.java)
        EasyMock.verify(environment, mockHandler)
    }
}

class OverrideDataLoaderModule : GraphQLApplicationModule() {
    override fun configureApplication() {
        dataLoaderRegistryFactoryBinder().setBinding().toInstance(
            KotlinDataLoaderRegistryFactory(
                object : KotlinDataLoader<String, String> {
                    override val dataLoaderName = "loader"
                    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<String, String> {
                        return DataLoaderFactory.newDataLoader { keys: List<String> ->
                            if (keys != listOf("a", "b")) {
                                throw IllegalArgumentException("wrong keys!")
                            }
                            CompletableFuture.completedFuture(
                                listOf(
                                    "1",
                                    "2",
                                ),
                            )
                        }
                    }
                },
            ),
        )
    }
}

@Guice(modules = [DefaultGraphQLModule::class, KMSModule::class, DummyModule::class, OverrideDataLoaderModule::class])
class GraphQLApplicationModuleDataLoaderOverrideTest
@Inject constructor(
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    val resources: Set<Any>,
) {
    @Test
    fun testBinding() {
        val graphQLResources = resources.filterIsInstance<GraphQLResource>()
        assertThat(graphQLResources).size().isGreaterThan(0)
        val factory = graphQLResources.first().dataLoaderRegistryFactory
        assertThat(factory).isNotNull()
        val loader = factory!!.generate(GraphQLContext.getDefault())
            .getDataLoader<String, String>("loader")
        assertThat(loader).isNotNull()
        val future = loader.loadMany(listOf("a", "b"))
        // an actual GraphQL resolver would return CompletableFuture<T> instead of T, and graphql-java would
        // deal with dispatching and awaiting as needed, but to make sure everything is hooked up right,
        // dispatch and await the future explicitly to assert on its returned value
        loader.dispatch()
        assertThat(future.get()).isEqualTo(listOf("1", "2"))
    }

    @Test
    fun testRequestToExecutionInputExtensionWithFactory() {
        val graphQLResources = resources.filterIsInstance<GraphQLResource>()
        val factory = graphQLResources.first().dataLoaderRegistryFactory
        val exampleQuery = GraphQLRequest("query {query}")
        val exampleContext = GraphQLContext.of(mapOf(String::class to "abc"))
        val executionInput = exampleQuery.toExecutionInput(exampleContext, factory!!.generate(exampleContext))
        assertThat(executionInput.dataLoaderRegistry.dataLoaders).isNotEmpty()
        assertThat(executionInput.query).isEqualTo(exampleQuery.query)
        assertThat(executionInput.graphQLContext.get<String>()).isEqualTo("abc")
    }

    @Test
    fun testRequestToExecutionInputExtensionWithoutFactory() {
        val exampleQuery = GraphQLRequest("query {query}")
        val exampleContext = GraphQLContext.of(mapOf(String::class to "abc"))
        val executionInput = exampleQuery.toExecutionInput(exampleContext)
        assertThat(executionInput.dataLoaderRegistry.dataLoaders).isEmpty()
        assertThat(executionInput.query).isEqualTo(exampleQuery.query)
        assertThat(executionInput.graphQLContext.get<String>()).isEqualTo("abc")
    }
}
