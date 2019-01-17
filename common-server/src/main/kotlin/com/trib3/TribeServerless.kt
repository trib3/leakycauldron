package com.trib3

import com.amazonaws.serverless.proxy.jersey.JerseyLambdaContainerHandler
import com.amazonaws.serverless.proxy.model.AwsProxyRequest
import com.amazonaws.serverless.proxy.model.AwsProxyResponse
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.trib3.config.TribeApplicationConfig
import com.trib3.healthchecks.VersionHealthCheck
import com.trib3.logging.RequestIdFilter
import com.trib3.modules.DefaultApplicationModule
import com.trib3.modules.ServerlessApplicationModule
import com.trib3.modules.TribeApplicationModule
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
    configurationFactoryFactory: ConfigurationFactoryFactory<@JvmSuppressWildcards Configuration>,
    @Named(TribeApplicationModule.APPLICATION_RESOURCES_BIND_NAME)
    jerseyResources: Set<@JvmSuppressWildcards Any>,
    servletFilters: Set<@JvmSuppressWildcards Class<out Filter>>,
    versionHealthCheck: VersionHealthCheck
) : Application<Configuration>() {
    val proxy: JerseyLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse>

    /*
     * statically initializes a TribeServerlessApp via guice using the modules specified in the application.conf
     */
    companion object {
        val INSTANCE: TribeServerlessApp

        init {
            val config = TribeApplicationConfig()
            val injector = config.getInjector(listOf(DefaultApplicationModule(), ServerlessApplicationModule()))
            INSTANCE = injector.getInstance(TribeServerlessApp::class.java)!!
        }
    }

    init {
        val bootstrap = Bootstrap(this)
        bootstrap.configurationFactoryFactory = configurationFactoryFactory
        bootstrap.registerMetrics()

        ServerlessCommand().run(bootstrap, Namespace(mapOf()))

        val resourceConfig = DropwizardResourceConfig(bootstrap.metricRegistry)
        jerseyResources.forEach { resourceConfig.register(it) }

        proxy = JerseyLambdaContainerHandler.getAwsProxyHandler(resourceConfig)

        proxy.onStartup {
            resourceConfig.setContextPath(proxy.servletContext.contextPath);
            servletFilters.forEach {
                proxy.servletContext.addFilter(it.simpleName, it)
                    .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*")
            }
        }

        log.info(
            "Initializing service {} in environment {} with version info: {} ",
            appConfig.serviceName,
            appConfig.env,
            versionHealthCheck.info()
        )
    }

    override fun getName(): String {
        return appConfig.serviceName
    }

    override fun run(configuration: Configuration?, environment: Environment?) {
        // Don't actually get called
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
        // Do nothing!
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