package com.trib3.server.config

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isNotNull
import com.authzee.kotlinguice4.getInstance
import com.trib3.config.KMSStringSelectReader
import com.trib3.config.modules.KMSModule
import org.testng.annotations.Test

class BootstrapConfigTest {
    @Test
    fun testConfig() {
        val config = BootstrapConfig()
        val injector = config.getInjector(listOf())
        assertThat(config.appModules).contains(KMSModule::class.qualifiedName)
        assertThat(injector).isNotNull()
        assertThat(injector.getInstance<KMSStringSelectReader>()).isNotNull()
    }
}
