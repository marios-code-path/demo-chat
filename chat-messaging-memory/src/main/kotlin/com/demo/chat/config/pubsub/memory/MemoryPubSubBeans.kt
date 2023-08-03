package com.demo.chat.config.pubsub.memory

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
class MemoryPubSubBeans<T, V>(val typeUtil: TypeUtil<T>) : PubSubServiceBeans<T, String> {

    override fun pubSubService(): TopicPubSubService<T, String> = MemoryTopicPubSubService()
}