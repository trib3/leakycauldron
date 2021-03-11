package com.trib3.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.trib3.testing.LeakyMock
import org.easymock.EasyMock
import org.testng.annotations.Test
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.DecryptResponse

const val ASSERT_VAL = "valvalval"

class ExtensionTest {
    val loader: ConfigLoader = ConfigLoader()

    init {
        val fakeKms = LeakyMock.mock<KmsClient>()
        EasyMock.expect(fakeKms.decrypt(EasyMock.anyObject(DecryptRequest::class.java)))
            .andReturn(DecryptResponse.builder().plaintext(SdkBytes.fromUtf8String(ASSERT_VAL)).build()).anyTimes()
        EasyMock.replay(fakeKms)
        KMSStringSelectReader(fakeKms)
    }

    @Test
    fun testExtension() {
        val config = loader.load()
        assertThat(config.extract<String>("encryptedobject.encryptedval")).isEqualTo(ASSERT_VAL)
        assertThat(config.extract<String>("encryptedobject.unencryptedval")).isEqualTo(ASSERT_VAL)
        val nestedConfig = config.getConfig("encryptedobject")
        assertThat(nestedConfig.extract<String>("encryptedval")).isEqualTo(ASSERT_VAL)
        assertThat(nestedConfig.extract<String>("unencryptedval")).isEqualTo(ASSERT_VAL)
    }
}
