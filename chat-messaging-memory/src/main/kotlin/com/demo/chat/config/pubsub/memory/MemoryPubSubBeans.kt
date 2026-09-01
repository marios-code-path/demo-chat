package com.demo.chat.config.pubsub.memory

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.TypeUtil
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Configuration plus Bean, not Component. The pubsub service keeps all state
// in instance maps. Spring injects this provider into the composite topic
// service, the composite message service, and the pubsub controller. A plain
// method call would give each holder a different instance, so opening a room
// in one instance would be invisible to a send in another. That produced
// NotFoundException on every send. fp issue B9, CHAT-ouzjdxun.
@Configuration
@ConditionalOnProperty(
    prefix = "app.service.core",
    name = ["pubsub"],
    havingValue = "memory",
    matchIfMissing = true
)
class MemoryPubSubBeans<T, V>(val typeUtil: TypeUtil<T>) : PubSubServiceBeans<T, String> {

    @Bean
    override fun pubSubService(): TopicPubSubService<T, String> = MemoryTopicPubSubService()
}