package com.demo.chat.test.messaging

import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.config.pubsub.redis.RedisPubSubBeans
import com.demo.chat.config.pubsub.redis.XStreamPubSubBeans
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.pubsub.impl.memory.messaging.RedisTopicPubSubService
import com.demo.chat.pubsub.impl.memory.messaging.XStreamTopicPubSubService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import java.util.UUID

/**
 * The selector `app.service.core.pubsub` must pick exactly one Redis-backed
 * [PubSubServiceBeans], and must pick none at all when another backend owns
 * the value. No Redis server is involved: the templates are built lazily and
 * nothing here issues a command.
 */
class RedisPubSubBeansSelectorTests {

    @Configuration
    class RedisInfrastructure {
        @Bean
        fun typeUtil(): TypeUtil<UUID> = UUIDUtil()

        @Bean
        fun redisTemplateConfiguration(): RedisTemplateConfiguration =
            RedisTemplateConfiguration(
                LettuceConnectionFactory(RedisStandaloneConfiguration("localhost", 6379))
                    .apply { afterPropertiesSet() },
                ObjectMapper().registerModule(KotlinModule.Builder().build())
            )
    }

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(
            RedisInfrastructure::class.java,
            RedisPubSubBeans::class.java,
            XStreamPubSubBeans::class.java
        )

    @Test
    fun `redis-pubsub selects the Pub-Sub implementation`() {
        runner
            .withPropertyValues("app.service.core.pubsub=redis-pubsub")
            .run { context ->
                assertThat(context).hasSingleBean(PubSubServiceBeans::class.java)
                assertThat(context.getBean(PubSubServiceBeans::class.java).pubSubService())
                    .isInstanceOf(RedisTopicPubSubService::class.java)
            }
    }

    @Test
    fun `redis-xstream selects the Streams implementation`() {
        runner
            .withPropertyValues("app.service.core.pubsub=redis-xstream")
            .run { context ->
                assertThat(context).hasSingleBean(PubSubServiceBeans::class.java)
                assertThat(context.getBean(PubSubServiceBeans::class.java).pubSubService())
                    .isInstanceOf(XStreamTopicPubSubService::class.java)
            }
    }

    @Test
    fun `a non-Redis selector activates neither Redis variant`() {
        runner
            .withPropertyValues("app.service.core.pubsub=memory")
            .run { context ->
                assertThat(context).doesNotHaveBean(PubSubServiceBeans::class.java)
            }
    }

    @Test
    fun `an unset selector activates neither Redis variant`() {
        runner.run { context ->
            assertThat(context).doesNotHaveBean(PubSubServiceBeans::class.java)
        }
    }
}
