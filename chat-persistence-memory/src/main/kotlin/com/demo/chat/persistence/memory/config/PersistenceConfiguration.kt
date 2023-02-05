package com.demo.chat.persistence.memory.config

import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.persistence.memory.impl.SecretsStoreInMemory
import com.demo.chat.service.security.UserCredentialSecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*
import java.util.function.Supplier

@Configuration
class PersistenceConfiguration {

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
    fun uuidTypeUtil(): TypeUtil<UUID> = UUIDUtil()

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    fun longTypeUtil(): TypeUtil<Long> = TypeUtil.LongUtil

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
    fun anonymousUUIDKeySupplier(): Supplier<Key<UUID>> = Supplier { Key.funKey(UUID(0L, 0L)) }

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    fun anonymousLongKeySupplier(): Supplier<Key<Long>> = Supplier { Key.funKey(0L) }


    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
    fun <T> passwordStoreInMemory(typeUtil: TypeUtil<T>): UserCredentialSecretsStore<T> = SecretsStoreInMemory()
}