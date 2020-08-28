package com.trib3.server.coroutine

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.trib3.testing.server.JettyWebTestContainerFactory
import com.trib3.testing.server.ResourceTestBase
import io.dropwizard.testing.common.Resource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.testng.annotations.Test
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.QueryParam
import javax.ws.rs.client.Entity
import javax.ws.rs.core.Response
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalStdlibApi::class)
@Path("/")
class InvocationHandlerTestResource {

    @Path("/regular")
    @GET
    fun regularMethod(): String {
        return "regular"
    }

    @Path("/regularQ")
    @GET
    fun regularQueryParameter(@QueryParam("q") q: String?): String {
        return "regular$q"
    }

    @Path("/regular")
    @POST
    fun regularPost(body: String): String {
        return "regular$body"
    }

    @Path("/coroutine")
    @GET
    suspend fun coroutineMethod(): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.Unconfined") {
            throw IllegalStateException("wrong dispatcher ${coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutine"
    }

    @Path("/coroutineQ")
    @GET
    suspend fun coroutineQueryParameter(@QueryParam("q") q: String?): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.Unconfined") {
            throw IllegalStateException("wrong dispatcher ${coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutine$q"
    }

    @Path("/coroutine")
    @POST
    suspend fun coroutinePost(body: String): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.Unconfined") {
            throw IllegalStateException("wrong dispatcher ${coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutine$body"
    }

    @Path("/coroutineDefault")
    @GET
    @AsyncDispatcher("Default")
    suspend fun coroutineMethodDefaultDispatcher(): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.Default") {
            throw IllegalStateException("wrong dispatcher ${coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutine"
    }

    @Path("/coroutineIO")
    @GET
    @AsyncDispatcher("IO")
    suspend fun coroutineMethodDefaultIO(): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.IO") {
            throw IllegalStateException("wrong dispatcher ${coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutine"
    }

    @Path("/coroutineMain")
    @GET
    @AsyncDispatcher("Main")
    suspend fun coroutineMethodDefaultMain(): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.Main") {
            throw IllegalStateException("wrong dispatcher ${coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutine"
    }

    @Path("/coroutineUnconfined")
    @GET
    @AsyncDispatcher("Unconfined")
    suspend fun coroutineMethodDefaultUnconfined(): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.Unconfined") {
            throw IllegalStateException("wrong dispatcher ${coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutine"
    }

    @Path("/coroutineException")
    @GET
    suspend fun coroutineMethodException(): String {
        delay(1)
        throw IllegalStateException("Bad implementation")
    }

    @Path("/coroutineCancelled")
    @GET
    suspend fun coroutineMethodCancelled(): String {
        coroutineContext.cancel()
        yield()
        return "coroutine"
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Path("/")
@AsyncDispatcher("Default")
class InvocationHandlerClassAnnotationTestResource {
    @Path("/coroutineClassAnnotationDefault")
    @GET
    suspend fun coroutineMethodDefaultDispatcher(): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.Default") {
            throw IllegalStateException("wrong dispatcher ${coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutineAnnotation"
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Path("/")
class InvocationHandlerClassScopeTestResource : CoroutineScope by CoroutineScope(Dispatchers.Default) {
    @Path("/coroutineClassScopeDefault")
    @GET
    suspend fun coroutineMethodDefaultDispatcher(): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.Default") {
            throw IllegalStateException("wrong dispatcher ${coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutineScope"
    }
}

class CoroutineInvocationHandlerTest : ResourceTestBase<InvocationHandlerTestResource>() {
    override fun getResource() = InvocationHandlerTestResource()

    override fun getContainerFactory(): TestContainerFactory {
        return JettyWebTestContainerFactory()
    }

    override fun buildAdditionalResources(resourceBuilder: Resource.Builder<*>) {
        resourceBuilder.addResource(CoroutineModelProcessor::class.java)
            .addResource(InvocationHandlerClassScopeTestResource())
            .addResource(InvocationHandlerClassAnnotationTestResource())
    }

    @Test
    fun testCoroutineMethod() {
        val ping = resource.target("/coroutine").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutine")
    }

    @Test
    fun testRegularQueryParameter() {
        val ping = resource.target("/regularQ").queryParam("q", "123").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("regular123")
    }

    @Test
    fun testRegularQueryParameterNull() {
        val ping = resource.target("/regularQ").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("regularnull")
    }

    @Test
    fun testCoroutineQueryParameter() {
        val ping = resource.target("/coroutineQ").queryParam("q", "123").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutine123")
    }

    @Test
    fun testCoroutineQueryParameterNull() {
        val ping = resource.target("/coroutineQ").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutinenull")
    }

    @Test
    fun testRegularMethod() {
        val ping = resource.target("/regular").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("regular")
    }

    @Test
    fun testRegularPost() {
        val ping = resource.target("/regular").request().post(Entity.entity("PostedBody", "text/plain"))
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("regularPostedBody")
    }

    @Test
    fun testCoroutinePost() {
        val ping = resource.target("/coroutine").request().post(Entity.entity("PostedBody", "text/plain"))
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutinePostedBody")
    }

    @Test
    fun testCoroutineMethodIODispatcher() {
        val ping = resource.target("/coroutineIO").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutine")
    }

    @Test
    fun testCoroutineMethodMainDispatcher() {
        val ping = resource.target("/coroutineMain").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutine")
    }

    @Test
    fun testCoroutineMethodUnconfinedDispatcher() {
        val ping = resource.target("/coroutineUnconfined").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutine")
    }

    @Test
    fun testCoroutineMethodDefaultDispatcher() {
        val ping = resource.target("/coroutineDefault").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutine")
    }

    @Test
    fun testCoroutineMethodClassAnnotationDefaultDispatcher() {
        val ping = resource.target("/coroutineClassAnnotationDefault").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutineAnnotation")
    }

    @Test
    fun testCoroutineMethodClassScopeDefaultDispatcher() {
        val ping = resource.target("/coroutineClassScopeDefault").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutineScope")
    }

    @Test
    fun testCoroutineMethodException() {
        val ping = resource.target("/coroutineException").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.statusCode)
        assertThat(ping.readEntity(String::class.java)).contains("There was an error processing your request")
    }

    @Test
    fun testCoroutineMethodCancel() {
        val ping = resource.target("/coroutineCancelled").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.SERVICE_UNAVAILABLE.statusCode)
    }
}

/**
 * Separate test with in memory container which doesn't support async resources methods
 * in order to test failure case
 */
class CoroutineInvocationHandlerCantSuspendTest : ResourceTestBase<InvocationHandlerTestResource>() {
    override fun getResource() = InvocationHandlerTestResource()

    override fun buildAdditionalResources(resourceBuilder: Resource.Builder<*>) {
        resourceBuilder.addResource(CoroutineModelProcessor::class.java)
    }

    @Test
    fun testCantSuspend() {
        val ping = resource.target("/coroutine").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.statusCode)
    }
}
