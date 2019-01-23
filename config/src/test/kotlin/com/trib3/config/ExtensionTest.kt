package com.trib3.config

import assertk.assert
import assertk.assertions.isEqualTo
import org.easymock.EasyMock
import org.testng.annotations.Test
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.DecryptResponse

const val ASSERT_VAL = "valvalval"

class ExtensionTest {
    init {
        val fakeKms = EasyMock.mock(KmsClient::class.java)
        EasyMock.expect(fakeKms.decrypt(EasyMock.anyObject(DecryptRequest::class.java)))
            .andReturn(DecryptResponse.builder().plaintext(SdkBytes.fromUtf8String(ASSERT_VAL)).build()).anyTimes()
        EasyMock.replay(fakeKms)
        KMSStringSelectReader(fakeKms)
    }

    @Test
    fun testExtension() {
        val config = ConfigLoader.load()
        assert(config.extract<String>("encryptedobject.encryptedval")).isEqualTo(ASSERT_VAL)
        assert(config.extract<String>("encryptedobject.unencryptedval")).isEqualTo(ASSERT_VAL)
        val nestedConfig = config.getConfig("encryptedobject")
        assert(nestedConfig.extract<String>("encryptedval")).isEqualTo(ASSERT_VAL)
        assert(nestedConfig.extract<String>("unencryptedval")).isEqualTo(ASSERT_VAL)

    }
}