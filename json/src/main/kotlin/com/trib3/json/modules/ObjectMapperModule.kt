package com.trib3.json.modules

import com.authzee.kotlinguice4.KotlinModule
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.json.ObjectMapperProvider

/**
 * Module for getting correctly configured [ObjectMapper]s
 */
class ObjectMapperModule : KotlinModule() {
    override fun configure() {
        bind<ObjectMapper>().toProvider<ObjectMapperProvider>()
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean {
        return other is ObjectMapperModule
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}
