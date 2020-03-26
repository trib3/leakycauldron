package com.trib3.db.flyway

import io.dropwizard.Configuration
import io.dropwizard.ConfiguredBundle
import io.dropwizard.setup.Environment
import org.flywaydb.core.api.configuration.FluentConfiguration
import javax.inject.Inject
import javax.sql.DataSource

/**
 * Dropwizard bundle for running flyway migrations at initialization time,
 * with configuration supplied by guice.
 */
class FlywayBundle
@Inject constructor(
    internal val dataSource: DataSource,
    internal val baseConfig: FluentConfiguration
) : ConfiguredBundle<Configuration> {

    override fun run(configuration: Configuration?, env: Environment?) {
        baseConfig.dataSource(dataSource).load().migrate()
    }
}
