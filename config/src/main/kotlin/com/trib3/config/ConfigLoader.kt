package com.trib3.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import javax.inject.Inject

/**
 * Helper class to allow for loading of config with environment based override support.
 * Follows default typesafe config rules for loading application.conf with fallback to
 * reference.conf, and then allows for environment blocks to override values.  Also
 * allows for an `overrides` block that will always override anything else set, which
 * is useful for conditional environment variable overrides.
 *
 * Example config:
 *
 * {
 *     abc: def
 *     ghi: klm
 *
 *     staging {
 *         abc: stagingdef
 *     }
 *     overrides: {
 *         ghi: ${?GHI_ENV_VAR}
 *     }
 * }
 *
 * The above config will:
 *     for `abc`: return "stagingdef" when run in staging, else will return "def"
 *     for `ghi`: return "klm" unless the GHI_ENV_VAR is set, in which case it will return its value
 *
 */
class ConfigLoader
constructor(
    private val defaultPath: String // usually only specified for tests
) {
    @Inject
    constructor() : this("")

    /**
     * Loads config from application.conf with environmental and global overrides
     */
    fun load(): Config {
        return load(ConfigFactory.load())
    }

    /**
     * Loads config from [path] inside application.conf with environmental and global overrides
     */
    fun load(path: String): Config {
        return load().getConfig(path)
    }

    /**
     * Loads config from [path] inside [fullConfig] with environmental and global overrides
     */
    fun load(fullConfig: Config, path: String): Config {
        return load(fullConfig).getConfig(path)
    }

    /**
     * Applies environmental and global overrides to the config
     */
    internal fun load(fullConfig: Config): Config {
        val env = fullConfig.extract("env") ?: "dev"
        val envOverride = env.split(",").map {
            fullConfig.extract(it) ?: ConfigFactory.empty()
        }.reduce { first, second -> first.withFallback(second) }
        val finalOverrides = fullConfig.extract("overrides") ?: ConfigFactory.empty()
        val fallbacks = envOverride.withFallback(fullConfig)
        // always have overrides take precedence, then fallback to defaultPath if there is one,
        // then fallback to env overrides, then fallback to fullConfig
        return if (defaultPath.isNotEmpty()) {
            finalOverrides.withFallback(fullConfig.getConfig(defaultPath)).withFallback(fallbacks)
        } else {
            finalOverrides.withFallback(fallbacks)
        }
    }
}
