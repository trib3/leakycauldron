package com.trib3.config

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.typesafe.config.ConfigFactory
import org.testng.annotations.Test

class ConfigLoaderTest {
    val loader = ConfigLoader(KMSStringSelectReader(null))

    @Test
    fun testDefaultLoad() {
        val config = loader.load()
        val testval = config.extract<String?>("testval")
        val env = config.extract<String?>("env")
        val devtest = config.extract<String?>("devtest")
        val overridefinal = config.extract<String?>("final")
        val enc = config.getString("encryptedobject.encryptedval")
        assertThat(env).isEqualTo("dev")
        assertThat(testval).isEqualTo("base")
        assertThat(devtest).isEqualTo("boom")
        assertThat(overridefinal).isEqualTo("zzz")
        assertThat(enc).isEqualTo("KMS(testtesttest)")

        // test case conversion
        assertThat(config.extract<String>("lowerCamel")).all {
            isEqualTo(config.extract<String>("lower-camel"))
            isEqualTo("test")
        }
    }

    @Test
    fun testPathedLoad() {
        val config = loader.load("subobject")
        val foo = config.extract<String?>("foo")
        val bar = config.extract<String?>("bar")
        val devfoo = config.extract<String?>("devfoo")
        assertThat(foo).isEqualTo("bar")
        assertThat(bar).isEqualTo("bazbam")
        assertThat(devfoo).isEqualTo("bam")
    }

    @Test
    fun testEnvOverrideLoad() {
        val envConfig = ConfigFactory.parseMap(mapOf("env" to "test")).withFallback(ConfigFactory.load())
        val config = loader.load(envConfig)
        val testval = config.extract<String?>("testval")
        val env = config.extract<String?>("env")
        val devtest = config.extract<String?>("devtest")
        val overridefinal = config.extract<String?>("final")
        assertThat(env).isEqualTo("test")
        assertThat(testval).isEqualTo("override")
        assertThat(devtest).isNull()
        assertThat(overridefinal).isEqualTo("zzz")
    }

    @Test
    fun testPathedEnvOverrideLoad() {
        val envConfig = ConfigFactory.parseMap(mapOf("env" to "test")).withFallback(ConfigFactory.load())
        val config = loader.load(envConfig, "subobject")
        val foo = config.extract<String?>("foo")
        val bar = config.extract<String?>("bar")
        val devfoo = config.extract<String?>("devfoo")
        assertThat(foo).isEqualTo("test")
        assertThat(bar).isEqualTo("baz")
        assertThat(devfoo).isNull()
    }

    @Test
    fun testMultiEnvOverrideLoad() {
        val envConfig = ConfigFactory.parseMap(mapOf("env" to "test,dev,unknown")).withFallback(ConfigFactory.load())
        val config = loader.load(envConfig)
        val testval = config.extract<String?>("testval")
        val env = config.extract<String?>("env")
        val devtest = config.extract<String?>("devtest")
        val overridefinal = config.extract<String?>("final")
        assertThat(env).isEqualTo("test,dev,unknown")
        assertThat(testval).isEqualTo("override")
        assertThat(devtest).isEqualTo("boom")
        assertThat(overridefinal).isEqualTo("zzz")
    }
}
