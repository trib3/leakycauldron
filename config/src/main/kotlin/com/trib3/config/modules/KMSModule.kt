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
}
