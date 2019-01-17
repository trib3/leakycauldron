package com.trib3.config

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.typesafe.config.ConfigFactory
import io.github.config4k.extract
import org.testng.annotations.Test

class ConfigLoaderTest {

    @Test
    fun testDefaultLoad() {
        val config = ConfigLoader.load()
        val testval = config.extract<String?>("testval")
        val env = config.extract<String?>("env")
        val devtest = config.extract<String?>("devtest")
        val overridefinal = config.extract<String?>("final")
        assert(env).isEqualTo("dev")
        assert(testval).isEqualTo("base")
        assert(devtest).isEqualTo("boom")
        assert(overridefinal).isEqualTo("zzz")
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