package com.trib3.graphql.modules

import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.size
import com.trib3.config.modules.KMSModule
import com.trib3.graphql.resources.GraphQLResource
import com.trib3.json.ObjectMapperProvider
import com.trib3.server.modules.TribeApplicationModule
import org.testng.annotations.Guice
import org.testng.annotations.Test
import javax.inject.Inject
import javax.inject.Named

class DummyQuery {
    fun query(): String {
        return "test"
    }
}

@Guice(modules = [DefaultGraphQLModule::class, KMSModule::class])
class GraphQLApplicationModuleTest
@Inject constructor(
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    val resources: Set<@JvmSuppressWildcards Any>
) {

    @Test
    fun testBinding() {
        assertThat(resources.filterIsInstance<GraphQLResource>()).size().isGreaterThan(0)
    }

    @Test
    fun testGraphQLProvider() {

        val module = DefaultGraphQLModule()
        val graphQLInstance = module.provideGraphQLInstance(
            setOf(),
            setOf(DummyQuery()),
            setOf(),
            setOf(),
            ObjectMapperProvider().get()
        )
        assertThat(graphQLInstance).isNotNull()
    }
}
