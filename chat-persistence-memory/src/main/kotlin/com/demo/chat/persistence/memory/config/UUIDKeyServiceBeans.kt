package com.demo.chat.persistence.memory.config

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.service.IKeyGenerator
import com.demo.chat.service.IKeyService
import com.demo.chat.persistence.memory.impl.KeyServiceInMemory
import org.springframework.context.annotation.Bean
import java.util.*

open class UUIDKeyServiceBeans : KeyServiceBeans<UUID> {
    private val idGenerator: IKeyGenerator<Long> = SnowflakeGenerator()

    @Bean
    override fun keyService(): IKeyService<UUID> =
        KeyServiceInMemory { UUID.nameUUIDFromBytes(idGenerator.nextKey().toString().encodeToByteArray()) }
}