package com.demo.chat.test.messaging

import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.pubsub.impl.memory.messaging.KeyConfigurationPubSub
import com.demo.chat.pubsub.impl.memory.messaging.TopicPubSubServiceRedis
import com.demo.chat.test.TestUUIDKeyService
import com.demo.chat.test.redis.TestContextConfiguration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.*
import java.util.function.Supplier

@Extensions(
    ExtendWith(SpringExtension::class)
)
@Import(TestContextConfiguration::class, PubSubBeanConfiguration::class)
@Testcontainers
class RedisPubSubMessagingTests(
    @Autowired pubsub: TopicPubSubService<UUID, String>,
) : PubSubTests<UUID, String>(pubsub, TestUUIDKeyService(), Supplier { "Test " })
{
    companion object {
        @Container
        var redisContainer: GenericContainer<*> =
            GenericContainer<Nothing>("redis:5.0.14")
                .apply {
                    withExposedPorts(6379)
                    waitingFor(
                        LogMessageWaitStrategy()
                            .withRegEx(".*Ready to accept connections.*\\s")
                            .withStartupTimeout(Duration.ofSeconds(60))
                    )
                    start()
                }

        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) {
            registry.add("spring.redis.host") { redisContainer.containerIpAddress }
            registry.add("spring.redis.port") { redisContainer.getMappedPort(6379) }
        }
    }

    @Test
    fun `Container Should Be Running`() {
        Assertions
            .assertThat(redisContainer.isRunning)
            .isTrue
    }
}

class PubSubBeanConfiguration {
    @Bean
    fun pubsubTests(configRedisTemplate: RedisTemplateConfiguration) =
        TopicPubSubServiceRedis(
            KeyConfigurationPubSub(
                "t_all_topics",
                "t_st_topic_",
                "t_l_user_topics_",
                "t_l_topic_users_"
            ),
            configRedisTemplate.stringTemplate(),
            configRedisTemplate.stringMessageTemplate(),
            StringUUIDKeyConverter(),
            UUIDKeyStringConverter()
        )
}