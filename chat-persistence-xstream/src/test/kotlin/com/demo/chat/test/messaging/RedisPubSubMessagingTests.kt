package com.demo.chat.test.messaging

import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.service.PubSubTopicExchangeService
import com.demo.chat.service.impl.memory.messaging.KeyConfigurationPubSub
import com.demo.chat.service.impl.memory.messaging.PubSubMessagingServiceRedisPubSub
import com.demo.chat.test.TestUUIDKeyService
import com.demo.chat.test.redis.EmbeddedRedisExtension
import com.demo.chat.test.redis.TestContextConfiguration
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*
import java.util.function.Supplier

@Extensions(
        ExtendWith(EmbeddedRedisExtension::class),
        ExtendWith(SpringExtension::class)
)
@Import(TestContextConfiguration::class, PubSubBeanConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisPubSubMessagingTests(
        @Autowired pubsub: PubSubTopicExchangeService<UUID, String>,
)
    : PubSubTests<UUID, String>(pubsub, TestUUIDKeyService(), Supplier { "Test " })

class PubSubBeanConfiguration {
    @Bean
    fun pubsubTests(configRedisTemplate: RedisTemplateConfiguration) =
            PubSubMessagingServiceRedisPubSub(
                    KeyConfigurationPubSub("t_all_topics",
                            "t_st_topic_",
                            "t_l_user_topics_",
                            "t_l_topic_users_"),
                    configRedisTemplate.stringTemplate(),
                    configRedisTemplate.stringMessageTemplate(),
                    StringUUIDKeyDecoder(),
                    UUIDKeyStringEncoder()
            )
}