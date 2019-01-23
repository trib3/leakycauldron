package com.trib3.server.config

import assertk.assert
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.authzee.kotlinguice4.getInstance
import com.trib3.config.KMSStringSelectReader
import com.trib3.config.modules.KMSModule
import org.testng.annotations.Test

class TribeApplicationConfigTest {
    @Test
    fun testConfig() {
        val config = TribeApplicationConfig()
        val injector = config.getInjector(listOf())
        assert(config.env).isEqualTo("dev")
        assert(config.serviceName).isEqualTo("Test")
        assert(config.serviceModules).contains(KMSModule::class.qualifiedName)
        assert(injector).isNotNull()
        assert(injector.getInstance<KMSStringSelectReader>()).isNotNull()
    }
}