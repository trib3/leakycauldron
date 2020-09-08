package com.trib3.testing.server

import com.trib3.json.ObjectMapperProvider
import io.dropwizard.auth.AuthValueFactoryProvider
import io.dropwizard.testing.common.Resource
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import java.security.Principal

private class Builder : Resource.Builder<Builder>() {
    public override fun buildResource(): Resource {
        val mapper = ObjectMapperProvider().get()
        this.setMapper(mapper)
        this.addProvider(AuthValueFactoryProvider.Binder(Principal::class.java))
        return super.buildResource()
    }
}

/**
 * Base class for testing jersey resources.  Sets up an in process jersey context,
 * and allows for customization of the container factory.  Binds an AuthValueFactoryProvider
 * so that resource methods can accept an `@Auth Principal` or `@Auth Optional<Principal>`
 * and uses Leaky Cauldron's default ObjectMapper.
 * Additional jersey resources can be added by overriding [buildAdditionalResources] as needed,
 * but most resource tests need only override [getResource] with the resource being tested.
 */
abstract class ResourceTestBase<T> {
    val resource: Resource by lazy {
        val resourceBuilder = Builder()
        resourceBuilder.setTestContainerFactory(getContainerFactory())
        // try to add the CoroutineModelProcessor without directly depending on the server jar
        runCatching {
            val modelProcessor = Class.forName("com.trib3.server.coroutine.CoroutineModelProcessor")
            resourceBuilder.addResource(modelProcessor)
        }
        resourceBuilder.addResource(getResource())
        buildAdditionalResources(resourceBuilder)
        System.setProperty("jersey.config.test.container.port", "0")
        resourceBuilder.buildResource()
    }

    abstract fun getResource(): T

    open fun getContainerFactory(): TestContainerFactory {
        return InMemoryTestContainerFactory()
    }

    open fun buildAdditionalResources(resourceBuilder: Resource.Builder<*>) {
        // do nothing
    }

    @BeforeClass
    open fun setUpClass() {
        resource.before()
    }

    @AfterClass
    open fun tearDownClass() {
        resource.after()
    }
}
