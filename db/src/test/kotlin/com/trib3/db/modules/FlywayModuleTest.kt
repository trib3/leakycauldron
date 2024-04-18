package com.trib3.db.modules

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.trib3.db.flyway.FlywayBundle
import dev.misfitlabs.kotlinguice4.KotlinModule
import io.dropwizard.core.Configuration
import io.dropwizard.core.ConfiguredBundle
import jakarta.inject.Inject
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.testng.annotations.Guice
import org.testng.annotations.Test

@Guice(modules = [FlywayModule::class])
class DefaultFlywayModuleTest
    @Inject
    constructor(
        val dropwizardBundles: Set<ConfiguredBundle<Configuration>>,
    ) {
        @Test
        fun testFlyway() {
            val flywayBundle = dropwizardBundles.filterIsInstance<FlywayBundle>().first()
            assertThat(flywayBundle.dataSource).isNotNull()
            assertThat(flywayBundle.baseConfig).isNotNull()
            assertThat(flywayBundle.baseConfig.sqlMigrationSuffixes.toList()).isEqualTo(listOf(".sql"))
        }

        @Test
        fun testEquals() {
            assertThat(FlywayModule()).isEqualTo(FlywayModule())
        }
    }

private class ConfiguredFlywayModule : KotlinModule() {
    override fun configure() {
        bind<FluentConfiguration>().toInstance(Flyway.configure().sqlMigrationSuffixes(".sql", ".test"))
    }
}

@Guice(modules = [FlywayModule::class, ConfiguredFlywayModule::class])
class ConfiguredFlywayModuleTest
    @Inject
    constructor(
        val dropwizardBundles: Set<ConfiguredBundle<Configuration>>,
    ) {
        @Test
        fun testFlyway() {
            val flywayBundle = dropwizardBundles.filterIsInstance<FlywayBundle>().first()
            assertThat(flywayBundle.dataSource).isNotNull()
            assertThat(flywayBundle.baseConfig).isNotNull()
            assertThat(flywayBundle.baseConfig.sqlMigrationSuffixes.toList()).isEqualTo(listOf(".sql", ".test"))
        }
    }
