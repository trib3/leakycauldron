package com.trib3.server

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.codahale.metrics.health.HealthCheckRegistry
import com.fasterxml.jackson.databind.DeserializationFeature
import com.google.inject.util.Providers
import com.trib3.config.ConfigLoader
import com.trib3.server.config.BootstrapConfig
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.filters.AdminAuthFilter
import com.trib3.server.filters.RequestIdFilter
import com.trib3.server.healthchecks.PingHealthCheck
import com.trib3.server.healthchecks.VersionHealthCheck
import com.trib3.server.modules.DefaultApplicationModule
import com.trib3.server.modules.DropwizardApplicationModule
import com.trib3.server.modules.EnvironmentCallback
import com.trib3.server.modules.ServletConfig
import com.trib3.server.modules.TribeApplicationModule
import com.trib3.testing.LeakyMock
import dev.misfitlabs.kotlinguice4.getInstance
import dev.misfitlabs.kotlinguice4.multibindings.KotlinMultibinder
import io.dropwizard.auth.AuthDynamicFeature
import io.dropwizard.auth.AuthFilter
import io.dropwizard.core.Configuration
import io.dropwizard.core.ConfiguredBundle
import io.dropwizard.core.setup.AdminEnvironment
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import io.dropwizard.core.sslreload.SslReloadBundle
import io.dropwizard.jersey.DropwizardResourceConfig
import io.dropwizard.jersey.setup.JerseyEnvironment
import io.dropwizard.jetty.MutableServletContextHandler
import io.dropwizard.jetty.setup.ServletEnvironment
import io.swagger.v3.jaxrs2.integration.OpenApiServlet
import jakarta.servlet.Filter
import jakarta.servlet.FilterRegistration
import jakarta.servlet.Servlet
import jakarta.servlet.ServletRegistration
import jakarta.ws.rs.container.ContainerRequestContext
import org.easymock.EasyMock
import org.eclipse.jetty.server.handler.CrossOriginHandler
import org.glassfish.jersey.internal.inject.InjectionManager
import org.testng.annotations.Test
import java.security.Principal

data class TestPrincipal(
    val _name: String,
) : Principal {
    override fun getName(): String = _name
}

class TestAuthFilter : AuthFilter<String, TestPrincipal>() {
    override fun filter(requestContext: ContainerRequestContext) {
        // do nothing
    }
}

class TestModule : TribeApplicationModule() {
    override fun configure() {
        authFilterBinder().setBinding().to<TestAuthFilter>()
        environmentCallbackBinder().addBinding().toInstance(EnvironmentCallback {})
        appServletBinder().addBinding().toInstance(
            ServletConfig(
                OpenApiServlet::class.java.simpleName,
                OpenApiServlet(),
                listOf("/openapi"),
            ),
        )
        KotlinMultibinder.newSetBinder<ConfiguredBundle<Configuration>>(kotlinBinder).addBinding().to<SslReloadBundle>()
        bind<InjectionManager>().toProvider(Providers.of(null))
    }
}

class TribeApplicationTest {
    val instance = TribeApplication.INSTANCE

    @Test
    fun testFields() {
        assertThat(instance.name).all {
            isEqualTo("Test")
            isEqualTo(instance.appConfig.appName)
        }
        assertThat(instance.healthChecks.map { it::class }).all {
            contains(VersionHealthCheck::class)
            contains(PingHealthCheck::class)
        }
        assertThat(instance.servletFilterConfigs.map { it.filterClass }).contains(RequestIdFilter::class.java)
        assertThat(instance.adminServletFilterConfigs.map { it.filterClass }).contains(AdminAuthFilter::class.java)
        assertThat(instance.adminServlets.map { it.name }).all {
            contains("SwaggerAssetServlet")
            contains(OpenApiServlet::class.simpleName)
        }
        assertThat(instance.versionHealthCheck).isNotNull()
        assertThat(instance.appServlets).isNotNull()
        assertThat(instance.envCallbacks).isNotNull()
        assertThat(instance.dropwizardBundles).isNotNull()
        assertThat(instance.jerseyResources).isNotNull()
        assertThat(instance.jaxrsAppProcessors).isNotNull()
        assertThat(instance.authFilter).isNull()
        assertThat(instance.rootHandler).isNotNull()
    }

    @Test
    fun testBootstrap() {
        val bootstrap = Bootstrap<Configuration>(instance)
        instance.initialize(bootstrap)
        assertThat(bootstrap.metricRegistry).isEqualTo(instance.metricRegistry)
        assertThat(bootstrap.healthCheckRegistry).isEqualTo(instance.healthCheckRegistry)
        assertThat(bootstrap.objectMapper).isEqualTo(instance.objectMapper)
        assertThat(bootstrap.objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse()
        assertThat(bootstrap.configurationFactoryFactory).all {
            isEqualTo(instance.configurationFactoryFactory)
            isInstanceOf(HoconConfigurationFactoryFactory::class)
        }
    }

    @Test
    fun testRun() {
        val mockConf = LeakyMock.mock<Configuration>()
        val mockEnv = LeakyMock.mock<Environment>()
        val mockJersey = LeakyMock.mock<JerseyEnvironment>()
        val mockAdmin = LeakyMock.mock<AdminEnvironment>()
        val mockServlet = LeakyMock.mock<ServletEnvironment>()
        val mockHealthChecks = LeakyMock.mock<HealthCheckRegistry>()
        val mockServletRegistration =
            LeakyMock.niceMock<ServletRegistration.Dynamic>()
        val mockFilterRegistration =
            LeakyMock.niceMock<FilterRegistration.Dynamic>()
        val mockAppContext = LeakyMock.mock<MutableServletContextHandler>()
        var authDynamicRegistered = false
        EasyMock.expect(mockEnv.jersey()).andReturn(mockJersey).anyTimes()
        EasyMock.expect(mockJersey.resourceConfig).andReturn(DropwizardResourceConfig()).anyTimes()
        EasyMock
            .expect(mockJersey.register(EasyMock.anyObject<Any?>()))
            .andAnswer {
                if (EasyMock.getCurrentArgument<Any>(0) is AuthDynamicFeature) {
                    authDynamicRegistered = true
                }
            }.anyTimes()
        EasyMock.expect(mockEnv.admin()).andReturn(mockAdmin).anyTimes()
        EasyMock
            .expect(mockAdmin.addServlet(LeakyMock.anyString(), LeakyMock.anyObject<Servlet>()))
            .andReturn(mockServletRegistration)
            .anyTimes()
        EasyMock.expect(mockEnv.servlets()).andReturn(mockServlet).anyTimes()
        EasyMock.expect(mockEnv.healthChecks()).andReturn(mockHealthChecks).anyTimes()
        EasyMock
            .expect(mockServlet.addFilter(LeakyMock.anyString(), EasyMock.anyObject<Class<out Filter>>()))
            .andReturn(mockFilterRegistration)
            .anyTimes()
        EasyMock
            .expect(mockServlet.addServlet(LeakyMock.anyString(), LeakyMock.anyObject<Servlet>()))
            .andReturn(mockServletRegistration)
            .anyTimes()
        EasyMock
            .expect(mockAdmin.addFilter(LeakyMock.anyString(), EasyMock.anyObject<Class<out Filter>>()))
            .andReturn(mockFilterRegistration)
            .anyTimes()
        EasyMock
            .expect(mockHealthChecks.register(LeakyMock.anyString(), LeakyMock.anyObject<VersionHealthCheck>()))
            .once()
        EasyMock.expect(mockHealthChecks.register(LeakyMock.anyString(), LeakyMock.anyObject<PingHealthCheck>())).once()
        EasyMock.expect(mockEnv.applicationContext).andReturn(mockAppContext)
        EasyMock.expect(mockAppContext.insertHandler(LeakyMock.isA<CrossOriginHandler>()))
        EasyMock.replay(
            mockConf,
            mockEnv,
            mockAdmin,
            mockJersey,
            mockServlet,
            mockHealthChecks,
            mockServletRegistration,
            mockFilterRegistration,
            mockAppContext,
        )
        instance.run(mockConf, mockEnv)
        assertThat(authDynamicRegistered).isFalse()
        EasyMock.verify(
            mockConf,
            mockEnv,
            mockAdmin,
            mockJersey,
            mockServlet,
            mockHealthChecks,
            mockServletRegistration,
            mockFilterRegistration,
        )
    }

    @Test
    fun testRunWithAuthFilter() {
        val authInstance =
            BootstrapConfig(ConfigLoader("withTestModule"))
                .getInjector(listOf(DefaultApplicationModule(), DropwizardApplicationModule()))
                .getInstance<TribeApplication>()
        val mockConf = LeakyMock.mock<Configuration>()
        val mockEnv = LeakyMock.mock<Environment>()
        val mockJersey = LeakyMock.mock<JerseyEnvironment>()
        val mockAdmin = LeakyMock.mock<AdminEnvironment>()
        val mockServlet = LeakyMock.mock<ServletEnvironment>()
        val mockHealthChecks = LeakyMock.mock<HealthCheckRegistry>()
        val mockServletRegistration =
            LeakyMock.niceMock<ServletRegistration.Dynamic>()
        val mockFilterRegistration =
            LeakyMock.niceMock<FilterRegistration.Dynamic>()
        val mockAppContext = LeakyMock.mock<MutableServletContextHandler>()
        var authDynamicRegistered = false
        EasyMock.expect(mockEnv.jersey()).andReturn(mockJersey).anyTimes()
        EasyMock.expect(mockJersey.resourceConfig).andReturn(DropwizardResourceConfig()).anyTimes()
        EasyMock
            .expect(mockJersey.register(EasyMock.anyObject<Any?>()))
            .andAnswer {
                if (EasyMock.getCurrentArgument<Any>(0) is AuthDynamicFeature) {
                    authDynamicRegistered = true
                }
            }.anyTimes()
        EasyMock.expect(mockEnv.admin()).andReturn(mockAdmin).anyTimes()
        EasyMock
            .expect(mockAdmin.addServlet(LeakyMock.anyString(), LeakyMock.anyObject<Servlet>()))
            .andReturn(mockServletRegistration)
            .anyTimes()
        EasyMock.expect(mockEnv.servlets()).andReturn(mockServlet).anyTimes()
        EasyMock.expect(mockEnv.healthChecks()).andReturn(mockHealthChecks).anyTimes()
        EasyMock
            .expect(mockServlet.addFilter(LeakyMock.anyString(), EasyMock.anyObject<Class<out Filter>>()))
            .andReturn(mockFilterRegistration)
            .anyTimes()
        EasyMock
            .expect(mockServlet.addServlet(LeakyMock.anyString(), LeakyMock.anyObject<Servlet>()))
            .andReturn(mockServletRegistration)
            .anyTimes()
        EasyMock
            .expect(mockAdmin.addFilter(LeakyMock.anyString(), EasyMock.anyObject<Class<out Filter>>()))
            .andReturn(mockFilterRegistration)
            .anyTimes()
        EasyMock
            .expect(mockHealthChecks.register(LeakyMock.anyString(), LeakyMock.anyObject<VersionHealthCheck>()))
            .once()
        EasyMock.expect(mockHealthChecks.register(LeakyMock.anyString(), LeakyMock.anyObject<PingHealthCheck>())).once()
        EasyMock.expect(mockEnv.applicationContext).andReturn(mockAppContext)
        EasyMock.expect(mockAppContext.insertHandler(LeakyMock.isA<CrossOriginHandler>()))

        EasyMock.replay(
            mockConf,
            mockEnv,
            mockAdmin,
            mockJersey,
            mockServlet,
            mockHealthChecks,
            mockServletRegistration,
            mockFilterRegistration,
            mockAppContext,
        )
        authInstance.initialize(Bootstrap<Configuration>(authInstance))
        authInstance.run(mockConf, mockEnv)
        assertThat(authDynamicRegistered).isTrue()
        EasyMock.verify(
            mockConf,
            mockEnv,
            mockAdmin,
            mockJersey,
            mockServlet,
            mockHealthChecks,
            mockServletRegistration,
            mockFilterRegistration,
        )
    }
}
