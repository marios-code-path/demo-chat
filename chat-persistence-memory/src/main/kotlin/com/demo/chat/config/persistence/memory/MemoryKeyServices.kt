package com.demo.chat.config.persistence.memory

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.persistence.memory.impl.KeyServiceInMemory
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["key"])
class MemoryKeyServices<T>(
    val keyGenerator: IKeyGenerator<T>
) : KeyServiceBeans<T> {

    @Bean
    override fun keyService(): IKeyService<T> =
        KeyServiceInMemory { keyGenerator.nextId() }
}



