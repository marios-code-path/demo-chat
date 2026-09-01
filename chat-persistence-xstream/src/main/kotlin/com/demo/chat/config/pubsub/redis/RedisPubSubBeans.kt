package com.demo.chat.config.pubsub.redis

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.domain.TypeUtil
import com.demo.chat.pubsub.impl.memory.messaging.KeyConfigurationPubSub
import com.demo.chat.pubsub.impl.memory.messaging.RedisTopicPubSubService
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Non-durable messaging over Redis Pub/Sub channels, selected by
 * `app.service.core.pubsub=redis-pubsub`.
 *
 * Configuration plus Bean, not Component. The service keeps its sinks and
 * Redis listeners in instance maps. Every holder of this provider must get
 * the same instance, or a subscription in one instance receives nothing from
 * another. fp issue B9, CHAT-ouzjdxun.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"], havingValue = "redis-pubsub")
class RedisPubSubBeans<T>(
    private val config: RedisTemplateConfiguration,
    private val typeUtil: TypeUtil<T>
) : PubSubServiceBeans<T, String> {

    @Bean
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
