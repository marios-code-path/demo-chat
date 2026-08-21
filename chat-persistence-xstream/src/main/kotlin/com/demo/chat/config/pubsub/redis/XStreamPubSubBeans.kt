package com.demo.chat.config.pubsub.redis

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.convert.Converter
import com.demo.chat.domain.TypeUtil
import com.demo.chat.pubsub.impl.memory.messaging.KeyConfiguration
import com.demo.chat.pubsub.impl.memory.messaging.XStreamTopicPubSubService
import com.demo.chat.service.core.TopicPubSubService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Durable messaging over Redis Streams, selected by
 * `app.service.core.pubsub=redis-xstream`.
 */
@Component
@ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"], havingValue = "redis-xstream")
class XStreamPubSubBeans<T>(
    private val config: RedisTemplateConfiguration,
    private val typeUtil: TypeUtil<T>
) : PubSubServiceBeans<T, String> {

    override fun pubSubService(): TopicPubSubService<T, String> =
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

    // The Streams implementation takes bidirectional key/String converters rather
    // than a TypeUtil, and no Converter beans exist in the deployment context.
    // TypeUtil already defines both directions, so derive them from it.
    private fun stringKeyConverter(): Converter<String, T> = object : Converter<String, T> {
        override fun convert(source: String): T = typeUtil.fromString(source)
    }

    private fun keyStringConverter(): Converter<T, String> = object : Converter<T, String> {
        override fun convert(source: T): String = typeUtil.toString(source)
    }
}
