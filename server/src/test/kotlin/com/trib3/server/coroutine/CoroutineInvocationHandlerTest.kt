package com.trib3.server.coroutine

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.annotation.Timed
import com.google.inject.Guice
import com.palominolabs.metrics.guice.MetricsInstrumentationModule
import com.trib3.testing.server.ResourceTestBase
import dev.misfitlabs.kotlinguice4.KotlinModule
import dev.misfitlabs.kotlinguice4.getInstance
import io.dropwizard.testing.common.Resource
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.container.AsyncResponse
import jakarta.ws.rs.container.Suspended
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.sse.Sse
import jakarta.ws.rs.sse.SseEventSink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.glassfish.jersey.media.sse.EventInput
import org.glassfish.jersey.server.ManagedAsync
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

private val sseScopes = ConcurrentHashMap<String, CoroutineScope>()

@OptIn(ExperimentalStdlibApi::class)
@Path("/")
open class InvocationHandlerTestResource {
    @Path("/regular")
    @GET
    fun regularMethod(): String {
        return "regular"
    }

    @Path("/regularQ")
    @GET
    fun regularQueryParameter(
        @QueryParam("q") q: String?,
    ): String {
        return "regular$q"
    }

    @Path("/regular")
    @POST
    fun regularPost(body: String): String {
        return "regular$body"
    }

    @Path("/coroutine")
    @GET
    @Timed
    open suspend fun coroutineMethod(): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.Unconfined") {
            throw IllegalStateException("wrong dispatcher ${coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutine"
    }

    @Path("/coroutineQ")
    @GET
    suspend fun coroutineQueryParameter(
        @QueryParam("q") q: Optional<String>,
    ): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.Unconfined") {
            throw IllegalStateException("wrong dispatcher ${coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutine${q.orElse("null")}"
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

    @Path("/sse")
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    suspend fun sse(
        @Context sseEventSink: SseEventSink,
        @Context sse: Sse,
        @QueryParam("q") q: String,
    ) = coroutineScope<Unit> {
        launch {
            sseScopes[q] = this
            try {
                var i = 0
                while (isActive) {
                    sseEventSink.send(sse.newEvent("i", i.toString()))
                    i++
                    delay(1)
                }
            } finally {
                sseScopes.remove(q)
            }
        }
    }

    @Path("/async")
    @GET
    @ManagedAsync
    suspend fun async(
        @Suspended async: AsyncResponse,
    ) {
        delay(1)
        async.resume("async")
    }

    @Path("/asyncError")
    @GET
    @ManagedAsync
    suspend fun asyncError(
        @Suspended async: AsyncResponse,
    ) {
        delay(1)
        async.toString()
        throw IllegalStateException("asyncoops")
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

    @Path("/coroutineClassAnnotationIOOverride")
    @GET
    @AsyncDispatcher("IO")
    suspend fun coroutineMethodIODispatcher(): String {
        if (coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.IO") {
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

    @Path("/coroutineClassScopeIOOverride")
    @GET
    @AsyncDispatcher("IO")
    suspend fun coroutineMethodIODispatcher(): String {
        if (kotlin.coroutines.coroutineContext[CoroutineDispatcher].toString() != "Dispatchers.IO") {
            throw IllegalStateException("wrong dispatcher ${kotlin.coroutines.coroutineContext[CoroutineDispatcher]}")
        }
        delay(1)
        return "coroutineScope"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineInvocationHandlerTest : ResourceTestBase<InvocationHandlerTestResource>() {
    private val mainDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @BeforeClass
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterClass
    fun tearDown() {
        Dispatchers.resetMain()
        mainDispatcher.close()
    }

    // create through guice  w/ metrics instrumentation so we get a dynamically subclassed instance
    override fun getResource(): InvocationHandlerTestResource {
        val injector =
            Guice.createInjector(
                object : KotlinModule() {
                    override fun configure() {
                        val registry = MetricRegistry()
                        bind<MetricRegistry>().toInstance(registry)
                        install(MetricsInstrumentationModule.builder().withMetricRegistry(registry).build())
                    }
                },
            )
        return injector.getInstance()
    }

    override fun buildAdditionalResources(resourceBuilder: Resource.Builder<*>) {
        resourceBuilder.addResource(CoroutineModelProcessor::class.java)
            .addResource(InvocationHandlerClassScopeTestResource::class.java)
            .addResource(InvocationHandlerClassAnnotationTestResource::class.java)
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
    fun testCoroutineMethodClassScopeIODispatcher() {
        val ping = resource.target("/coroutineClassScopeIOOverride").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutineScope")
    }

    @Test
    fun testCoroutineMethodClassAnnotationIODispatcher() {
        val ping = resource.target("/coroutineClassAnnotationIOOverride").request().get()
        assertThat(ping.status).isEqualTo(Response.Status.OK.statusCode)
        assertThat(ping.readEntity(String::class.java)).isEqualTo("coroutineAnnotation")
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

    @Test
    fun testSseMethodCancel() {
        val q = UUID.randomUUID().toString()
        val sse = resource.target("/sse").queryParam("q", q).request().get(EventInput::class.java)
        assertThat(sseScopes[q]).isNotNull()
        val event = sse.read()
        assertThat(event.readData()).isEqualTo("0")
        sse.close()
        // wait up to one second for the resource method to be cancelled and cleaned up
        val now = System.currentTimeMillis()
        while (sseScopes[q] != null) {
            assertThat(System.currentTimeMillis() - now).isLessThan(1000)
            Thread.sleep(1)
        }
        assertThat(sseScopes[q]).isNull()
    }

    @Test
    fun testAsync() {
        val async = resource.target("/async").request().get(String::class.java)
        assertThat(async).isEqualTo("async")
    }

    @Test
    fun testAsyncError() {
        assertFailure {
            resource.target("/asyncError").request().get(String::class.java)
        }
    }
}
