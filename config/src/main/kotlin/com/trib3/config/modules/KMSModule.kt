package com.trib3.config.modules

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Provides
import com.trib3.config.KMSStringSelectReader
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient
import javax.inject.Singleton

class KMSModule : KotlinModule() {

    override fun configure() {
        bind<KMSStringSelectReader>().`in`<Singleton>()
    }

    @Provides
    fun provideKms(): KmsClient {
        return KmsClient.builder()
            .region(Region.US_EAST_1)
            .build()
    }

    // allow multiple installations so that multiple other modules can install this one
    override fun equals(other: Any?): Boolean {
        return other is KMSModule
    }

    override fun hashCode(): Int {
        return this::class.hashCode()
    }
}
