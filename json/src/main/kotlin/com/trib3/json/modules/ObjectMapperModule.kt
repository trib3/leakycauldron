package com.trib3.json.modules

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Binder
import com.google.inject.multibindings.MapBinder
import com.google.inject.name.Names
import com.trib3.json.ObjectMapperProvider
import dev.misfitlabs.kotlinguice4.KotlinModule
import dev.misfitlabs.kotlinguice4.typeLiteral
import kotlin.reflect.KClass

/**
 * Module for getting correctly configured [ObjectMapper]s
 */
class ObjectMapperModule : KotlinModule() {
    override fun configure() {
        bind<ObjectMapper>().toProvider<ObjectMapperProvider>()
        // create an empty map by default
        objectMapperMixinBinder { binder() }
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean = other is ObjectMapperModule

    override fun hashCode(): Int = this::class.hashCode()

    companion object {
        /**
         * Binder for Jackson Mixins, call from configure() like:
         *     objectMapperMixinBinder { binder() }.addBinding(ClassToDecorate::class).toInstance(MixinClass::class)
         */
        fun objectMapperMixinBinder(binder: () -> Binder): MapBinder<KClass<*>, KClass<*>> =
            MapBinder
                .newMapBinder(
                    binder(),
                    typeLiteral<KClass<*>>(),
                    typeLiteral<KClass<*>>(),
                    Names.named(ObjectMapperProvider.OBJECT_MAPPER_MIXINS),
                )
    }
}
