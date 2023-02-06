package com.demo.chat.persistence.memory.config

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.persistence.memory.impl.KeyServiceInMemory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
@Configuration
open class UUIDKeyServiceBeans : KeyServiceBeans<UUID> {
    private val idGenerator: IKeyGenerator<Long> = SnowflakeGenerator()

    @Bean
    override fun keyService(): IKeyService<UUID> =
        KeyServiceInMemory { UUID.nameUUIDFromBytes(idGenerator.nextKey().toString().encodeToByteArray()) }
}