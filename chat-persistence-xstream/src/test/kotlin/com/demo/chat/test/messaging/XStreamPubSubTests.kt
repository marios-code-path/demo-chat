package com.demo.chat.test.messaging

import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.service.PubSubTopicExchangeService
import com.demo.chat.service.impl.memory.messaging.KeyConfiguration
import com.demo.chat.service.impl.memory.messaging.PubSubTopicExchangeRedisStream
import com.demo.chat.test.TestUUIDKeyService
import com.demo.chat.test.redis.EmbeddedRedisExtension
import com.demo.chat.test.redis.TestContextConfiguration
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
import java.util.*
import java.util.function.Supplier

@Extensions(
        ExtendWith(EmbeddedRedisExtension::class),
        ExtendWith(SpringExtension::class)
)
@Import(TestContextConfiguration::class, XStreamBeanConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XStreamPubSubTests(@Autowired pubsub: PubSubTopicExchangeService<UUID, String>)
    : PubSubTests<UUID, String>(pubsub, TestUUIDKeyService(), Supplier { "Test " }) {
    @BeforeAll
    fun hooks() {
        Hooks.onOperatorDebug()
    }
}

class XStreamBeanConfiguration {
    @Bean
    fun topicService(configRedisTemplate: RedisTemplateConfiguration) = PubSubTopicExchangeRedisStream(
            KeyConfiguration("all_topics",
                    "st_topic_",
                    "l_user_topics_",
                    "l_topic_users_"),
            configRedisTemplate.stringTemplate(),
            configRedisTemplate.stringMessageTemplate(),
            StringUUIDKeyDecoder(),
            UUIDKeyStringEncoder()
    )
}