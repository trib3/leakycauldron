package com.trib3.graphql.modules

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.size
import com.trib3.config.modules.KMSModule
import com.trib3.graphql.execution.GraphQLRequest
import com.trib3.graphql.resources.GraphQLResource
import com.trib3.json.ObjectMapperProvider
import com.trib3.server.filters.RequestIdFilter
import com.trib3.server.modules.TribeApplicationModule
import graphql.GraphQL
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import org.testng.annotations.Guice
import org.testng.annotations.Test
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Named

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
                DataLoaderDispatcherInstrumentationOptions.newOptions().includeStatistics(true)
            )
        )
    }
}

@Guice(modules = [DefaultGraphQLModule::class, KMSModule::class, DummyModule::class])
class GraphQLApplicationModuleTest
@Inject constructor(
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    val resources: Set<@JvmSuppressWildcards Any>,
    val graphQL: GraphQL
) {
    @Test
    fun testBinding() {
        val graphQLResources = resources.filterIsInstance<GraphQLResource>()
        assertThat(graphQLResources).size().isGreaterThan(0)
        assertThat(graphQLResources.first().dataLoaderRegistryFactory).isNull()
        RequestIdFilter.withRequestId("graphQLInstrumentationBindingTest") {
            val result = graphQL.execute("query test")
            assertThat(result.extensions["RequestId"]).isEqualTo("graphQLInstrumentationBindingTest")
            assertThat(result.extensions["dataloader"]).isNotNull()
            val x = result.extensions["dataloader"] as Map<*, *>
            assertThat(x["overall-statistics"]).isNotNull()
            assertThat(x["individual-statistics"]).isNotNull()
        }
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
        ObjectMapperProvider().get()
    )
    assertThat(graphQLInstance).isNotNull()
}

class OverrideDataLoaderModule : GraphQLApplicationModule() {
    override fun configureApplication() {
        dataLoaderRegistryFactoryBinder().setBinding().toInstance { _, _ ->
            val registry = DataLoaderRegistry()
            registry.register(
                "loader",
                DataLoader.newDataLoader(BatchLoader { keys: List<String> ->
                    if (keys != listOf("a", "b")) {
                        throw IllegalArgumentException("wrong keys!")
                    }
                    CompletableFuture.completedFuture(
                        listOf(
                            "1",
                            "2"
                        )
                    )
                })
            )
            registry
        }
    }
}

@Guice(modules = [DefaultGraphQLModule::class, KMSModule::class, DummyModule::class, OverrideDataLoaderModule::class])
class GraphQLApplicationModuleDataLoaderOverrideTest
@Inject constructor(
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    val resources: Set<@JvmSuppressWildcards Any>
) {
    @Test
    fun testBinding() {
        val graphQLResources = resources.filterIsInstance<GraphQLResource>()
        assertThat(graphQLResources).size().isGreaterThan(0)
        val factory = graphQLResources.first().dataLoaderRegistryFactory
        assertThat(factory).isNotNull()
        val loader = factory!!.invoke(GraphQLRequest("", mapOf(), ""), null).getDataLoader<String, String>("loader")
        assertThat(loader).isNotNull()
        val future = loader.loadMany(listOf("a", "b"))
        // an actual GraphQL resolver would return CompletableFuture<T> instead of T, and graphql-java would
        // deal with dispatching and awaiting as needed, but to make sure everything is hooked up right,
        // dispatch and await the future explicitly to assert on its returned value
        loader.dispatch()
        assertThat(future.get()).isEqualTo(listOf("1", "2"))
    }
}
