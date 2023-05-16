package com.trib3.db.flyway

import io.dropwizard.core.Configuration
import io.dropwizard.core.ConfiguredBundle
import io.dropwizard.core.setup.Environment
import jakarta.inject.Inject
import org.flywaydb.core.api.configuration.FluentConfiguration
import javax.sql.DataSource

/**
 * Dropwizard bundle for running flyway migrations at initialization time,
 * with configuration supplied by guice.
 */
class FlywayBundle
@Inject constructor(
    internal val dataSource: DataSource,
    internal val baseConfig: FluentConfiguration,
) : ConfiguredBundle<Configuration> {

    override fun run(configuration: Configuration?, env: Environment?) {
        baseConfig.dataSource(dataSource).load().migrate()
    }
}
