package com.trib3.server.resources

import com.trib3.json.ObjectMapperProvider
import io.dropwizard.testing.common.Resource
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass

private class Builder : Resource.Builder<Builder>() {
    public override fun buildResource(): Resource {
        val mapper = ObjectMapperProvider().get()
        this.setMapper(mapper)
        return super.buildResource()
    }
}

/**
 * Base class for testing jersey resources.  Sets up an in process jersey context
 *
 */
abstract class ResourceTestBase<T> {
    lateinit var resource: Resource

    abstract fun getResource(): T

    @BeforeClass
    open fun setUpClass() {
        val resourceBuilder = Builder()
        resourceBuilder.addResource(getResource())
        resource = resourceBuilder.buildResource()
        resource.before()
    }

    @AfterClass
    open fun tearDownClass() {
        resource.after()
    }
}
