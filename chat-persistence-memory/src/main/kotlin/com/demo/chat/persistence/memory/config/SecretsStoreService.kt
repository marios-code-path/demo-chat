package com.demo.chat.persistence.memory.config

import com.demo.chat.domain.TypeUtil
import com.demo.chat.persistence.memory.impl.SecretsStoreInMemory
import com.demo.chat.service.security.UserCredentialSecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SecretsStoreService {

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["secrets"])
    fun <T> passwordStoreInMemory(typeUtil: TypeUtil<T>): UserCredentialSecretsStore<T> = SecretsStoreInMemory()
}