package com.trib3.json

import com.codahale.metrics.json.MetricsModule
import com.fasterxml.jackson.annotation.JacksonInject
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair
import com.fasterxml.jackson.module.guice.GuiceInjectableValues
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Injector
import com.trib3.json.ObjectMapperProvider.Companion.OBJECT_MAPPER_MIXINS
import com.trib3.json.jackson.Guice7AnnotationIntrospector
import com.trib3.json.jackson.ThreeTenExtraModule
import io.dropwizard.jackson.Jackson
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Provider
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * A provider that provides an ObjectMapper that has been configured
 * for tribe:  it's compatible with dropwizard, kotlin, java8 time
 * classes, and permissive on unknown properties.
 *
 * Allows injecting mixins by providing a Map<KClass, KClass> bound
 * by name [OBJECT_MAPPER_MIXINS]
 *
 * When created as part of a Guice [Injector], will bridge jackson
 * [com.fasterxml.jackson.databind.InjectableValues] and guice bindings,
 * so that deserialized objects with [JacksonInject] annotated members
 * are injected from the guice bindings.
 */

class ObjectMapperProvider @Inject constructor(
    @Named(OBJECT_MAPPER_MIXINS)
    private val mixins: Map<KClass<*>, KClass<*>>,
    private val injector: Injector?,
) : Provider<ObjectMapper> {
    constructor() : this(emptyMap(), null)

    companion object {
        const val OBJECT_MAPPER_MIXINS = "ObjectMapperMixins"
    }

    override fun get(): ObjectMapper {
        val mapper = Jackson.newObjectMapper()
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        mapper.disable(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        mapper.registerKotlinModule()
        mapper.registerModule(MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, false))
        mapper.registerModule(ThreeTenExtraModule())

        // set up guice <-> jackson inject bridge like jackson's guice module does
        if (injector != null) {
            val guiceIntrospector = Guice7AnnotationIntrospector()
            mapper.injectableValues = GuiceInjectableValues(injector)
            mapper.setAnnotationIntrospectors(
                AnnotationIntrospectorPair(
                    guiceIntrospector,
                    mapper.serializationConfig.annotationIntrospector,
                ),
                AnnotationIntrospectorPair(
                    guiceIntrospector,
                    mapper.deserializationConfig.annotationIntrospector,
                ),
            )
        }
        mixins.forEach {
            mapper.addMixIn(it.key.java, it.value.java)
        }
        return mapper
    }
}
