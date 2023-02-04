package com.demo.chat.pubsub.config

import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.TopicPubSubService
import com.demo.chat.pubsub.impl.memory.messaging.MemoryTopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PubSubConfig {
    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
    fun <T> memoryPubSub(typeUtil: TypeUtil<T>): TopicPubSubService<T, String> = MemoryTopicPubSubService()
}