package com.demo.chat.config.deploy.redis

import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.convert.Converter
import com.demo.chat.domain.TypeUtil
import com.demo.chat.pubsub.impl.memory.messaging.KeyConfiguration
import com.demo.chat.pubsub.impl.memory.messaging.KeyConfigurationPubSub
import com.demo.chat.pubsub.impl.memory.messaging.RedisTopicPubSubService
import com.demo.chat.pubsub.impl.memory.messaging.XStreamTopicPubSubService
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the Redis-backed [TopicPubSubService] selected by `app.service.core.pubsub`:
 *
 *  - `redis-pubsub` - non-durable Redis Pub/Sub channels, lowest latency.
 *  - `redis-xstream` - durable Redis Streams, capped replay depth.
 *
 * The value space is flat and shared across backends (`memory`, `kafka`,
 * `redis-pubsub`, `redis-xstream`); only `memory` carries the default.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
class TopicMessagingConfiguration<T>(
    private val config: RedisTemplateConfiguration,
    private val typeUtil: TypeUtil<T>
) {

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"], havingValue = "redis-pubsub")
    fun topicMessagingRedisPubSub(): TopicPubSubService<*, *> =
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

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"], havingValue = "redis-xstream")
    fun topicMessagingRedisXStream(): TopicPubSubService<*, *> =
        XStreamTopicPubSubService(
            KeyConfiguration(
                "all_topics",
                "st_topic_",
                "l_user_topics_",
                "l_topic_users_"
            ),
            config.stringTemplate(),
            config.stringMessageTemplate(),
            stringKeyConverter(),
            keyStringConverter()
        )

    // The stream variant takes key<->String converters rather than a TypeUtil, and
    // the deployment context declares no Converter beans. TypeUtil already defines
    // both directions, so derive the converters from it instead of demanding beans
    // that would have to be added to every Redis deployment.
    private fun stringKeyConverter(): Converter<String, T> = object : Converter<String, T> {
        override fun convert(source: String): T = typeUtil.fromString(source)
    }

    private fun keyStringConverter(): Converter<T, String> = object : Converter<T, String> {
        override fun convert(source: T): String = typeUtil.toString(source)
    }
}
