package com.trib3.server.modules

import com.authzee.kotlinguice4.multibindings.KotlinMultibinder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.server.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.server.logging.RequestIdFilter
import io.dropwizard.Configuration
import io.dropwizard.configuration.ConfigurationFactoryFactory
import io.dropwizard.jackson.Jackson
import javax.servlet.Filter

/**
 * The default guice module, binds things common to dropwizard and serverless execution
 */
class DefaultApplicationModule : TribeApplicationModule() {
    override fun configure() {
        // bind HOCON configuration parser
        bind<ConfigurationFactoryFactory<Configuration>>().to<HoconConfigurationFactoryFactory<Configuration>>()

        val filterBinder = KotlinMultibinder.newSetBinder<Class<out Filter>>(kotlinBinder)
        filterBinder.addBinding().toInstance(RequestIdFilter::class.java)

        val mapper = Jackson.newObjectMapper()
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        bind<ObjectMapper>().toInstance(mapper)

        // Make sure the resource binder is set up
        resourceBinder()
    }
}
