package com.trib3.db.modules

import com.authzee.kotlinguice4.KotlinModule
import com.authzee.kotlinguice4.KotlinPrivateModule
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import com.google.inject.name.Names
import com.trib3.config.ConfigLoader
import com.trib3.config.modules.KMSModule
import com.trib3.db.config.DbConfig
import org.jooq.DSLContext
import java.util.Objects
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.sql.DataSource

/**
 * Provides a [DbConfig] with the appropriate [configPath]
 */
private class DbConfigProvider
@Inject constructor(
    private val loader: ConfigLoader,
    @Named("configPath") private val configPath: String,
    private val healthCheckRegistry: HealthCheckRegistry,
    private val metricRegistry: MetricRegistry
) : Provider<DbConfig> {
    override fun get(): DbConfig {
        return DbConfig(loader, configPath, healthCheckRegistry, metricRegistry)
    }
}

/**
 * Provides a [DataSource] from the [DbConfig] with the appropriate [configPath]
 */
private class DataSourceProvider
@Inject constructor(
    private val loader: ConfigLoader,
    @Named("configPath") private val configPath: String,
    private val healthCheckRegistry: HealthCheckRegistry,
    private val metricRegistry: MetricRegistry
) : Provider<DataSource> {
    override fun get(): DataSource {
        return DbConfig(loader, configPath, healthCheckRegistry, metricRegistry).dataSource
    }
}

/**
 * Provides a [DSLContext] from the [DbConfig] with the appropriate [configPath]
 */
private class DSLContextProvider
@Inject constructor(
    private val loader: ConfigLoader,
    @Named("configPath") private val configPath: String,
    private val healthCheckRegistry: HealthCheckRegistry,
    private val metricRegistry: MetricRegistry
) : Provider<DSLContext> {
    override fun get(): DSLContext {
        return DbConfig(loader, configPath, healthCheckRegistry, metricRegistry).dslContext
    }
}

/**
 * Private module that implements the bindings for [NamedDbModule] and allows for multiple
 * installations of [NamedDbModule]s with different [name]s
 */
private class PrivateDbModule(private val name: String) : KotlinPrivateModule() {
    override fun configure() {
        bindConstant().annotatedWith(Names.named("configPath")).to(name)
        bind<DbConfig>().annotatedWith(Names.named(name)).toProvider<DbConfigProvider>()
        bind<DataSource>().annotatedWith(Names.named(name)).toProvider<DataSourceProvider>()
        bind<DSLContext>().annotatedWith(Names.named(name)).toProvider<DSLContextProvider>()
        expose<DbConfig>().annotatedWith(Names.named(name))
        expose<DataSource>().annotatedWith(Names.named(name))
        expose<DSLContext>().annotatedWith(Names.named(name))
    }
}

/**
 * Similar to [DbModule], but allows for configuration of the [DbConfig]'s configPath in the
 * application.conf
 *
 * The [DbConfig], [DataSource], and [DSLContext] bound by this are @[Named] by the [name],
 * so usage of them looks something like:
 *
 *     install(NamedDbModule("redshift"))
 *
 *     class RedshiftDAO @Inject constructor (@Named("redshift") val redshiftCtx: DSLContext)
 */
class NamedDbModule(val name: String) : KotlinModule() {
    override fun configure() {
        install(KMSModule())
        install(PrivateDbModule(name))
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean {
        return other is NamedDbModule && other.name == this.name
    }

    override fun hashCode(): Int {
        return Objects.hash(this::class.hashCode(), this.name)
    }
}
