package com.demo.chat.test.messaging

import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.pubsub.impl.memory.messaging.KeyConfiguration
import com.demo.chat.pubsub.impl.memory.messaging.XStreamTopicPubSubService
import com.demo.chat.test.TestUUIDKeyService
import com.demo.chat.test.redis.TestContextConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.*
import java.util.function.Supplier

@Testcontainers
@Extensions(
    ExtendWith(SpringExtension::class)
)
@Import(TestContextConfiguration::class, XStreamBeanConfiguration::class)
@Tag("integration")
class XStreamPubSubTests(@Autowired pubsub: TopicPubSubService<UUID, String>) :
    PubSubTests<UUID, String>(pubsub, TestUUIDKeyService(), Supplier { "Test " }) {

    @Test
    fun `delivers a stream message sent after open`() {
        val topic = UUID.randomUUID()
        val sender = UUID.randomUUID()
        val message = Message.create(
            MessageKey.create(UUID.randomUUID(), sender, topic),
            "stream-payload",
            true,
        )

        StepVerifier.create(messaging.listenTo(topic))
            .then { messaging.open(topic).block(Duration.ofSeconds(10)) }
            .then { messaging.sendMessage(message).block(Duration.ofSeconds(10)) }
            .assertNext { actual ->
                assertThat(actual.key.id).isEqualTo(message.key.id)
                assertThat(actual.key.from).isEqualTo(sender)
                assertThat(actual.key.dest).isEqualTo(topic)
                assertThat(actual.data).isEqualTo("stream-payload")
                assertThat(actual.record).isTrue()
            }
            .thenCancel()
            .verify(Duration.ofSeconds(15))
    }

    @Test
    fun `repeated open delivers one stream message once`() {
        val topic = UUID.randomUUID()
        val message = Message.create(
            MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), topic),
            "repeated-open-payload",
            true,
        )

        StepVerifier.create(messaging.listenTo(topic))
            .then {
                messaging.open(topic).block(Duration.ofSeconds(10))
                messaging.open(topic).block(Duration.ofSeconds(10))
                messaging.sendMessage(message).block(Duration.ofSeconds(10))
            }
            .assertNext { actual -> assertThat(actual.key.id).isEqualTo(message.key.id) }
            .expectNoEvent(Duration.ofMillis(500))
            .thenCancel()
            .verify(Duration.ofSeconds(5))
    }

    @Test
    fun `concurrent open delivers one stream message once`() {
        val topic = UUID.randomUUID()
        val message = Message.create(
            MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), topic),
            "concurrent-open-payload",
            true,
        )

        StepVerifier.create(messaging.listenTo(topic))
            .then {
                Flux.merge(messaging.open(topic), messaging.open(topic))
                    .then(messaging.sendMessage(message))
                    .block(Duration.ofSeconds(10))
            }
            .assertNext { actual -> assertThat(actual.key.id).isEqualTo(message.key.id) }
            .expectNoEvent(Duration.ofMillis(500))
            .thenCancel()
            .verify(Duration.ofSeconds(5))
    }

    @Test
    fun `close stops the reader and a later open starts a new reader`() {
        val topic = UUID.randomUUID()

        StepVerifier.create(messaging.listenTo(topic))
            .then {
                messaging.open(topic).block(Duration.ofSeconds(10))
                messaging.close(topic).block(Duration.ofSeconds(10))
            }
            .verifyComplete()

        val message = Message.create(
            MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), topic),
            "reopened-payload",
            true,
        )

        StepVerifier.create(messaging.listenTo(topic))
            .then {
                messaging.open(topic).block(Duration.ofSeconds(10))
                messaging.sendMessage(message).block(Duration.ofSeconds(10))
            }
            .assertNext { actual -> assertThat(actual.key.id).isEqualTo(message.key.id) }
            .thenCancel()
            .verify(Duration.ofSeconds(5))
    }

    companion object {
        @Container
        private var redisContainer: GenericContainer<*> =
            GenericContainer<Nothing>("redis:5.0.12-alpine").apply {
                withExposedPorts(6379)
                start()
            }

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
    fun topicService(configRedisTemplate: RedisTemplateConfiguration) = XStreamTopicPubSubService(
        KeyConfiguration(
            "all_topics",
            "st_topic_",
            "l_user_topics_",
            "l_topic_users_"
        ),
        configRedisTemplate.stringTemplate(),
        configRedisTemplate.stringMessageTemplate(),
        StringUUIDKeyConverter(),
        UUIDKeyStringConverter()
    )
}
