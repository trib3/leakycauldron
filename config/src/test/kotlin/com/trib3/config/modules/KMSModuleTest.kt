package com.trib3.config.modules

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.testng.annotations.Guice
import org.testng.annotations.Test
import software.amazon.awssdk.services.kms.KmsClient
import javax.inject.Inject

@Guice(modules = [KMSModule::class])
class KMSModuleTest
@Inject constructor(
    val kmsClient: KmsClient
) {
    @Test
    fun testBind() {
        assertThat(kmsClient).isNotNull()
    }

    @Test
    fun testEquals() {
        assertThat(KMSModule()).isEqualTo(KMSModule())
    }
}
