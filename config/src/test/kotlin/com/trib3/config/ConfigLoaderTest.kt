package com.trib3.config

import assertk.all
import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.typesafe.config.ConfigFactory
import org.testng.annotations.Test

class ConfigLoaderTest {

    @Test
    fun testDefaultLoad() {
        KMSStringSelectReader._INSTANCE = KMSStringSelectReader(null)
        val config = ConfigLoader.load()
        val testval = config.extract<String?>("testval")
        val env = config.extract<String?>("env")
        val devtest = config.extract<String?>("devtest")
        val overridefinal = config.extract<String?>("final")
        val enc = config.extract<String>("encryptedobject.encryptedval")
        assert(env).isEqualTo("dev")
        assert(testval).isEqualTo("base")
        assert(devtest).isEqualTo("boom")
        assert(overridefinal).isEqualTo("zzz")
        assert(enc).isEqualTo("KMS(testtesttest)")

        // test case conversion
        assert(config.extract<String>("lowerCamel")).all {
            isEqualTo(config.extract<String>("lower-camel"))
            isEqualTo("test")
        }
    }

    @Test
    fun testPathedLoad() {
        val config = ConfigLoader.load("subobject")
        val foo = config.extract<String?>("foo")
        val bar = config.extract<String?>("bar")
        val devfoo = config.extract<String?>("devfoo")
        assert(foo).isEqualTo("bar")
        assert(bar).isEqualTo("bazbam")
        assert(devfoo).isEqualTo("bam")
    }

    @Test
    fun testEnvOverrideLoad() {
        val oldEnv = System.setProperty("env", "test")
        ConfigFactory.invalidateCaches()
        try {
            val config = ConfigLoader.load()
            val testval = config.extract<String?>("testval")
            val env = config.extract<String?>("env")
            val devtest = config.extract<String?>("devtest")
            val overridefinal = config.extract<String?>("final")
            assert(env).isEqualTo("test")
            assert(testval).isEqualTo("override")
            assert(devtest).isNull()
            assert(overridefinal).isEqualTo("zzz")
        } finally {
            if (oldEnv == null) {
                System.clearProperty("env")
            } else {
                System.setProperty("env", oldEnv)
            }
            ConfigFactory.invalidateCaches()
        }
    }

    @Test
    fun testPathedEnvOverrideLoad() {
        val oldEnv = System.setProperty("env", "test")
        ConfigFactory.invalidateCaches()
        try {
            val config = ConfigLoader.load("subobject")
            val foo = config.extract<String?>("foo")
            val bar = config.extract<String?>("bar")
            val devfoo = config.extract<String?>("devfoo")
            assert(foo).isEqualTo("test")
            assert(bar).isEqualTo("baz")
            assert(devfoo).isNull()
        } finally {
            if (oldEnv == null) {
                System.clearProperty("env")
            } else {
                System.setProperty("env", oldEnv)
            }
            ConfigFactory.invalidateCaches()
        }
    }

    @Test
    fun testMultiEnvOverrideLoad() {
        val oldEnv = System.setProperty("env", "test,dev")
        ConfigFactory.invalidateCaches()
        try {
            val config = ConfigLoader.load()
            val testval = config.extract<String?>("testval")
            val env = config.extract<String?>("env")
            val devtest = config.extract<String?>("devtest")
            val overridefinal = config.extract<String?>("final")
            assert(env).isEqualTo("test,dev")
            assert(testval).isEqualTo("override")
            assert(devtest).isEqualTo("boom")
            assert(overridefinal).isEqualTo("zzz")
        } finally {
            if (oldEnv == null) {
                System.clearProperty("env")
            } else {
                System.setProperty("env", oldEnv)
            }
            ConfigFactory.invalidateCaches()
        }
    }
}