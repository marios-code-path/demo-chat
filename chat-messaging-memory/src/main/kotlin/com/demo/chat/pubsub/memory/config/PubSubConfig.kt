package com.demo.chat.pubsub.memory.config

import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class PubSubConfig {
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
    fun <T> memoryPubSub(typeUtil: TypeUtil<T>): TopicPubSubService<T, String> = MemoryTopicPubSubService()
}