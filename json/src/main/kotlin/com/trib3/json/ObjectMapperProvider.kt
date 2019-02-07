package com.trib3.json

import com.codahale.metrics.json.MetricsModule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.dropwizard.jackson.Jackson
import java.util.concurrent.TimeUnit
import javax.inject.Provider

/**
 * A provider that provides an ObjectMapper that has been configured
 * for tribe:  it's compatible with dropwizard, kotlin, java8 time
 * classes, and permissive on unknown properties
 */
class ObjectMapperProvider : Provider<ObjectMapper> {
    override fun get(): ObjectMapper {
        val mapper = Jackson.newObjectMapper()
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        mapper.registerModule(KotlinModule())
        mapper.registerModule(MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, false))
        return mapper
    }
}
