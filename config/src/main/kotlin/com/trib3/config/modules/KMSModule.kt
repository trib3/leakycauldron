package com.trib3.config.modules

import com.google.inject.AbstractModule
import com.google.inject.Provides
import software.amazon.awssdk.services.kms.KmsClient

class KMSModule : AbstractModule() {
    @Provides
    fun provideKms(): KmsClient {
        return KmsClient.builder().build()
    }
}