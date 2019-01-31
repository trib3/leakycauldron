package com.trib3.config

import com.google.common.base.CaseFormat
import com.typesafe.config.Config
import io.github.config4k.ClassContainer
import io.github.config4k.readers.SelectReader
import mu.KotlinLogging
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.DecryptRequest
import java.util.Base64
import javax.inject.Inject

private val log = KotlinLogging.logger { }
private val base64 = Base64.getDecoder()!!

class KMSStringReader(private val kms: KmsClient?) {
    fun getValue(config: Config, path: String): String? {
        if (config.hasPath(path)) {
            val rawValue = config.getString(path)
            return process(rawValue, path)
        }

        // If path not present, try converting it to hyphenated case and try again. This is the
        // preferred format for HOCON files.
        val hyphenPath = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, path)
        if (config.hasPath(hyphenPath)) {
            val rawValue = config.getString(hyphenPath)
            return process(rawValue, hyphenPath)
        }

        // Else return null
        return null
    }

    fun process(rawValue: String, path: String): String {
        if (rawValue.startsWith("KMS(") && rawValue.endsWith(")")) {
            if (kms != null) {
                val rawKms = SdkBytes.fromByteArray(
                    base64.decode(
                        rawValue.substring("KMS(".length, rawValue.length - 1)
                    )
                )
                val decryptRequest = DecryptRequest.builder().ciphertextBlob(rawKms).build()
                return kms.decrypt(decryptRequest).plaintext().asUtf8String()
            } else {
                log.warn(
                    "trying to decrypt KMS config value without a configured kmsClient, " +
                        "returning raw value at path {}", path
                )
            }
        }
        return rawValue
    }
}

class KMSStringSelectReader
@Inject constructor(private val kms: KmsClient?) {

    companion object {
        var _INSTANCE: KMSStringSelectReader = KMSStringSelectReader(null)
        val INSTANCE: KMSStringSelectReader
            get() = _INSTANCE
    }

    init {
        if (_INSTANCE == null || _INSTANCE.kms == null) {
            _INSTANCE = this // first non-null kms instance wins
        }
    }

    /**
     * Add new case to support new type.
     *
     * @param clazz a instance got from the given type by reflection
     * @throws Config4kException.UnSupportedType if the passed type is not supported
     */
    fun getReader(clazz: ClassContainer): (Config, String) -> Any? {
        return when (clazz.mapperClass) {
            String::class -> KMSStringReader(kms)::getValue
            else ->
                SelectReader.getReader(clazz)
        }
    }
}
