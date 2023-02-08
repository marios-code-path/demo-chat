package com.demo.chat.persistence.memory.config

import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.UUIDKeyGenerator
import com.demo.chat.service.core.IKeyGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
class KeyGenConfiguration {
    // enforce number on nodeid
    @Value("\${app.nodeid:0}")
    lateinit var nodeId: String

    @ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
    @Bean("KeyGenerator")
    fun uuidGenerator(): IKeyGenerator<UUID> = UUIDKeyGenerator(nodeId.toInt())

    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    @Bean("KeyGenerator")
    fun longGenerator(): IKeyGenerator<Long> = LongKeyGenerator(nodeId.toInt())
}