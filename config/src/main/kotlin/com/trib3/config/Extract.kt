package com.trib3.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import io.github.config4k.ClassContainer
import io.github.config4k.TypeReference

/**
 * An extension function that extends the [io.github.config4k.extract] extension function with
 * the ability to put KMS() encrypted config strings in config.  Note that in order to get an
 * encrypted value to be unencrypted, you must extract<String>, and not extract<ComplexObject>
 * where ComplexObject contains a String.
 */
inline fun <reified T> Config.extract(path: String): T {
    val genericType = object : TypeReference<T>() {}.genericType()

    val result = KMSStringSelectReader.INSTANCE.getReader(ClassContainer(T::class, genericType))(this, path)

    return try {
        result as T
    } catch (e: Exception) {
        throw result?.let { e } ?: ConfigException.BadPath(
            path,
            "take a look at your config",
        )
    }
}
