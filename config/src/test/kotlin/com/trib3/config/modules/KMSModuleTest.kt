package com.trib3.config.modules

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.google.inject.Guice
import com.trib3.config.ASSERT_VAL
import com.trib3.config.KMSStringSelectReader
import com.trib3.testing.LeakyMock
import dev.misfitlabs.kotlinguice4.getInstance
import org.easymock.EasyMock
import org.testng.annotations.Test
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.DecryptResponse

class KMSModuleTest {
    @Test
    fun testBind() {
        val fakeKms =
            LeakyMock.mock<KmsClient>().also {
                EasyMock.expect(it.decrypt(EasyMock.anyObject(DecryptRequest::class.java)))
                    .andReturn(
                        DecryptResponse.builder().plaintext(SdkBytes.fromUtf8String(ASSERT_VAL)).build(),
                    ).anyTimes()
                EasyMock.replay(it)
            }
        // construct a KMSStringSelectReader to fill in _INSTANCE kms so real kms doesn't get inserted for other tests
        KMSStringSelectReader(fakeKms)
        val kmsClient = Guice.createInjector(KMSModule()).getInstance<KmsClient>()
        assertThat(kmsClient).isNotNull()
    }

    @Test
    fun testEquals() {
        assertThat(KMSModule()).isEqualTo(KMSModule())
    }
}
