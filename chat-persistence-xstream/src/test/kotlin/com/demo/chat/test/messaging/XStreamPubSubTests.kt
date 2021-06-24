package com.demo.chat.test.messaging

import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.service.PubSubService
import com.demo.chat.service.impl.memory.messaging.KeyConfiguration
import com.demo.chat.service.impl.memory.messaging.PubSubTopicExchangeRedisStream
import com.demo.chat.test.TestUUIDKeyService
import com.demo.chat.test.redis.TestContextConfiguration
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.publisher.Hooks
import java.util.*
import java.util.function.Supplier

@Testcontainers
@Extensions(
    ExtendWith(SpringExtension::class)
)
@Import(TestContextConfiguration::class, XStreamBeanConfiguration::class)
class XStreamPubSubTests(@Autowired pubsub: PubSubService<UUID, String>) :
    PubSubTests<UUID, String>(pubsub, TestUUIDKeyService(), Supplier { "Test " }) {

    companion object {
        @Container
        private var redisContainer: GenericContainer<*> =
            GenericContainer<Nothing>("redis:5.0.12-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) {
            registry.add("spring.redis.host") { redisContainer.containerIpAddress }
            registry.add("spring.redis.port") { redisContainer.getMappedPort(6379) }
        }
    }
}

class XStreamBeanConfiguration {
    @Bean
    fun topicService(configRedisTemplate: RedisTemplateConfiguration) = PubSubTopicExchangeRedisStream(
        KeyConfiguration(
            "all_topics",
            "st_topic_",
            "l_user_topics_",
            "l_topic_users_"
        ),
        configRedisTemplate.stringTemplate(),
        configRedisTemplate.stringMessageTemplate(),
        StringUUIDKeyDecoder(),
        UUIDKeyStringEncoder()
    )
}