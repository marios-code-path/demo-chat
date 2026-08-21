package com.demo.chat.config.pubsub.redis

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.domain.TypeUtil
import com.demo.chat.pubsub.impl.memory.messaging.KeyConfigurationPubSub
import com.demo.chat.pubsub.impl.memory.messaging.RedisTopicPubSubService
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Non-durable messaging over Redis Pub/Sub channels, selected by
 * `app.service.core.pubsub=redis-pubsub`.
 */
@Component
@ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"], havingValue = "redis-pubsub")
class RedisPubSubBeans<T>(
    private val config: RedisTemplateConfiguration,
    private val typeUtil: TypeUtil<T>
) : PubSubServiceBeans<T, String> {

    override fun pubSubService(): TopicPubSubService<T, String> =
        RedisTopicPubSubService(
            KeyConfigurationPubSub(
                "all_topics",
                "st_topic_",
                "l_user_topics_",
                "l_topic_users_"
            ),
            config.stringTemplate(),
            config.stringMessageTemplate(),
            typeUtil
        )
}
