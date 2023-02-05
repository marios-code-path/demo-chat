package com.demo.chat.persistence.memory.config

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.service.IKeyGenerator
import com.demo.chat.persistence.memory.impl.KeyServiceInMemory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("app.service.core.key", havingValue = "long")
open class LongKeyServiceBeans : KeyServiceBeans<Long> {
    private val idGenerator: IKeyGenerator<Long> = SnowflakeGenerator()

    @Bean
    override fun keyService() = KeyServiceInMemory { idGenerator.nextKey() }
}