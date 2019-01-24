package com.trib3.server

import com.amazonaws.serverless.proxy.jersey.JerseyLambdaContainerHandler
import com.amazonaws.serverless.proxy.model.AwsProxyRequest
import com.amazonaws.serverless.proxy.model.AwsProxyResponse
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.authzee.kotlinguice4.getInstance
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.server.config.TribeApplicationConfig
import com.trib3.server.healthchecks.VersionHealthCheck
import com.trib3.server.logging.RequestIdFilter
import com.trib3.server.modules.DefaultApplicationModule
import com.trib3.server.modules.ServerlessApplicationModule
import com.trib3.server.modules.TribeApplicationModule
import io.dropwizard.Application
import io.dropwizard.Configuration
import io.dropwizard.cli.ConfiguredCommand
import io.dropwizard.configuration.ConfigurationFactoryFactory
import io.dropwizard.jersey.DropwizardResourceConfig
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import mu.KotlinLogging
import net.sourceforge.argparse4j.inf.Namespace
import org.slf4j.MDC
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Named
import javax.servlet.DispatcherType
import javax.servlet.Filter

private val log = KotlinLogging.logger { }

/**
 * Exposes the dropwizard application's jersey context as a serverless handler
 */
class TribeServerlessApp @Inject constructor(
    val appConfig: TribeApplicationConfig,
    val objectMapper: ObjectMapper,
    internal val configurationFactoryFactory: ConfigurationFactoryFactory<@JvmSuppressWildcards Configuration>,
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    internal val jerseyResources: Set<@JvmSuppressWildcards Any>,
    internal val servletFilters: Set<@JvmSuppressWildcards Class<out Filter>>,
    internal val versionHealthCheck: VersionHealthCheck
) : Application<Configuration>() {

    val bootstrap = Bootstrap(this)
    val proxy: JerseyLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse>

    /*
     * statically initializes a TribeServerlessApp via guice using the modules specified in the application.conf
     */
    companion object {
        val INSTANCE: TribeServerlessApp

        init {
            val config = TribeApplicationConfig()
            val injector = config.getInjector(listOf(DefaultApplicationModule(), ServerlessApplicationModule()))
            INSTANCE = injector.getInstance<TribeServerlessApp>()
        }
    }

    init {
        initialize(bootstrap)

        ServerlessCommand().run(bootstrap, Namespace(mapOf()))
        proxy = run()
    }

    override fun getName(): String {
        return appConfig.serviceName
    }

    override fun initialize(bootstrap: Bootstrap<Configuration>) {
        bootstrap.configurationFactoryFactory = configurationFactoryFactory
        bootstrap.objectMapper = objectMapper
        bootstrap.registerMetrics()
    }

    fun run(): JerseyLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> {
        val resourceConfig = DropwizardResourceConfig(bootstrap.metricRegistry)
        jerseyResources.forEach { resourceConfig.register(it) }

        val newProxy = JerseyLambdaContainerHandler.getAwsProxyHandler(resourceConfig)

        newProxy.onStartup {
            resourceConfig.setContextPath(newProxy.servletContext.contextPath)
            servletFilters.forEach {
                newProxy.servletContext.addFilter(it.simpleName, it)
                    .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*")
            }
        }

        log.info(
            "Initializing service {} in environment {} with version info: {} ",
            appConfig.serviceName,
            appConfig.env,
            versionHealthCheck.info()
        )
        return newProxy
    }

    override fun run(configuration: Configuration?, environment: Environment?) {
        // do nothing
    }
}

/**
 * Simple command that lets TribeServerless leverage dropwizard to set up basic bootstrap environment
 */
class ServerlessCommand : ConfiguredCommand<Configuration>(
    "serverless",
    "Command that allows dropwizard bootstrap to happen, but runs nothing"
) {
    override fun run(bootstrap: Bootstrap<Configuration>?, namespace: Namespace?, configuration: Configuration?) {
        // do nothing
    }
}

/**
 * Main entry point for AWS lambda
 */
class TribeServerless : RequestHandler<AwsProxyRequest, AwsProxyResponse> {
    /**
     * Handles the lambda function request by passing it to the jersey proxy
     */
    override fun handleRequest(input: AwsProxyRequest?, context: Context?): AwsProxyResponse {
        MDC.put(RequestIdFilter.REQUEST_ID_KEY, context?.awsRequestId)
        try {
            return TribeServerlessApp.INSTANCE.proxy.proxy(input, context)
        } finally {
            MDC.remove("RequestId")
        }
    }
}
