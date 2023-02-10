package com.demo.chat.config.deploy.redis

import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.domain.TypeUtil
import com.demo.chat.pubsub.impl.memory.messaging.KeyConfigurationPubSub
import com.demo.chat.pubsub.impl.memory.messaging.TopicPubSubServiceRedis
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["app.service.core.pubsub"])
class TopicMessagingConfiguration<T>(
    private val config: RedisTemplateConfiguration,
    private val typeUtil: TypeUtil<T>
) {

    @Bean
    fun topicMessagingRedisPubSub(): TopicPubSubService<*, *> =
        TopicPubSubServiceRedis(
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