package com.trib3.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.typesafe.config.ConfigFactory
import org.easymock.EasyMock
import org.testng.annotations.Test
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import software.amazon.awssdk.services.kms.model.DecryptResponse

class KMSStringReaderTest {
    @Test
    fun testNoKMS() {
        val reader = KMSStringReader(null)
        val config = ConfigFactory.parseMap(mapOf("test" to "KMS(blah)"))
        assertThat(reader.getValue(config, "test")).isEqualTo("KMS(blah)")
    }

    @Test
    fun testFakeKMS() {
        val mockKms = EasyMock.createMock<KmsClient>(KmsClient::class.java)
        EasyMock.expect(mockKms.decrypt(EasyMock.anyObject(DecryptRequest::class.java)))
            .andReturn(DecryptResponse.builder().plaintext(SdkBytes.fromUtf8String("bleh")).build()).anyTimes()
        EasyMock.replay(mockKms)
        val reader = KMSStringReader(mockKms)
        val config = ConfigFactory.parseMap(
            mapOf(
                "test" to "KMS(blah)",
                "openOnly" to "KMS(blah",
                "closeOnly" to "blah)"
            )
        )
        assertThat(reader.getValue(config, "test")).isEqualTo("bleh")
        assertThat(reader.getValue(config, "openOnly")).isEqualTo("KMS(blah")
        assertThat(reader.getValue(config, "closeOnly")).isEqualTo("blah)")
    }
}
