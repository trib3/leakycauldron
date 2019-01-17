package com.trib3.modules

import com.google.inject.TypeLiteral
import com.google.inject.multibindings.Multibinder
import com.trib3.config.dropwizard.HoconConfigurationFactoryFactory
import com.trib3.logging.RequestIdFilter
import io.dropwizard.Configuration
import io.dropwizard.configuration.ConfigurationFactoryFactory
import javax.servlet.Filter

/**
 * The default guice module, binds things common to dropwizard and serverless execution
 */
class DefaultApplicationModule : TribeApplicationModule() {
    override fun configure() {
        // bind HOCON configuration parser
        bind(object : TypeLiteral<ConfigurationFactoryFactory<Configuration>>() {})
            .to(object : TypeLiteral<HoconConfigurationFactoryFactory<Configuration>>() {})
        val filterBinder = Multibinder.newSetBinder(binder(),
            object : TypeLiteral<Class<out Filter>>() {})
        filterBinder.addBinding().toInstance(RequestIdFilter::class.java)
    }
}