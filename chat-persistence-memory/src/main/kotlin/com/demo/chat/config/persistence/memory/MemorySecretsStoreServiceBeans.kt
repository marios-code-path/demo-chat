package com.demo.chat.config.persistence.memory

import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.persistence.memory.impl.SecretsStoreInMemory
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.service.security.UserCredentialSecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["secrets"])
class MemorySecretsStoreServiceBeans<T> : SecretsStoreBeans<T> {

    @Bean
    override fun secretsStore(): SecretsStore<T> = SecretsStoreInMemory()
}