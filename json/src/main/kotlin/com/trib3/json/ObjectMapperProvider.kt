package com.trib3.json

import com.codahale.metrics.json.MetricsModule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.trib3.json.ObjectMapperProvider.Companion.OBJECT_MAPPER_MIXINS
import com.trib3.json.jackson.ThreeTenExtraModule
import io.dropwizard.jackson.Jackson
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * A provider that provides an ObjectMapper that has been configured
 * for tribe:  it's compatible with dropwizard, kotlin, java8 time
 * classes, and permissive on unknown properties.
 *
 * Allows injecting mixins by providing a Map<KClass, KClass> bound
 * by name [OBJECT_MAPPER_MIXINS]
 */

class ObjectMapperProvider @Inject constructor(
    @Named(OBJECT_MAPPER_MIXINS)
    private val mixins: Map<@JvmSuppressWildcards KClass<*>, @JvmSuppressWildcards KClass<*>>
) : Provider<ObjectMapper> {
    constructor() : this(emptyMap())

    companion object {
        const val OBJECT_MAPPER_MIXINS = "ObjectMapperMixins"
    }

    override fun get(): ObjectMapper {
        val mapper = Jackson.newObjectMapper()
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        mapper.disable(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        mapper.registerModule(KotlinModule())
        mapper.registerModule(MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, false))
        mapper.registerModule(ThreeTenExtraModule())
        mixins.forEach {
            mapper.addMixIn(it.key.java, it.value.java)
        }
        return mapper
    }
}
