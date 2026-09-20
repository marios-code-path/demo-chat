package com.demo.chat.test.messaging

import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.pubsub.impl.memory.messaging.KeyConfigurationPubSub
import com.demo.chat.pubsub.impl.memory.messaging.RedisTopicPubSubService
import com.demo.chat.service.core.TopicPubSubService
import com.demo.chat.test.TestUUIDKeyService
import com.demo.chat.test.redis.TestContextConfiguration
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
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
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

@Extensions(
    ExtendWith(SpringExtension::class)
)
@Import(TestContextConfiguration::class, PubSubBeanConfiguration::class)
@Testcontainers
@Tag("integration")
class RedisPubSubMessagingTests(
    @Autowired pubsub: TopicPubSubService<UUID, String>,
    @Autowired private val redisTemplates: RedisTemplateConfiguration,
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

    @Test
    fun `concurrent open creates one channel reader`() {
        val topic = UUID.randomUUID()
        val message = Message.create(
            MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), topic),
            "concurrent-open-payload",
            true,
        )

        StepVerifier.create(messaging.listenTo(topic))
            .then {
                Flux.merge(
                    messaging.open(topic).subscribeOn(Schedulers.parallel()),
                    messaging.open(topic).subscribeOn(Schedulers.parallel()),
                )
                    .then()
                    .block(Duration.ofSeconds(10))
                assertThat(
                    redisTemplates.stringMessageTemplate<UUID>()
                        .convertAndSend(topic.toString(), message)
                        .block(Duration.ofSeconds(10))
                ).isEqualTo(1L)
            }
            .assertNext { actual -> assertThat(actual.key.id).isEqualTo(message.key.id) }
            .expectNoEvent(Duration.ofMillis(500))
            .thenCancel()
            .verify(Duration.ofSeconds(5))
    }

    @Test
    fun `close disposes the channel reader`() {
        val topic = UUID.randomUUID()
        messaging.open(topic).block(Duration.ofSeconds(10))

        val reader = activeDisposable("sources", topic)

        messaging.close(topic).block(Duration.ofSeconds(10))

        assertThat(reader.isDisposed).isTrue()
    }

    /**
     * A reader failure must reach the listeners of that topic.
     *
     * A listener that simply stops receiving cannot tell a dead reader
     * apart from a quiet topic. The sink carries the error instead.
     * See CHAT-czmjffen.
     */
    @Test
    fun `a reader failure ends the listener with an error`() {
        val topic = UUID.randomUUID()
        val channel = Sinks.many().multicast().onBackpressureBuffer<Message<UUID, String>>()
        val service = serviceReading { channel.asFlux() }

        StepVerifier.create(service.listenTo(topic))
            .then { service.open(topic).block(Duration.ofSeconds(10)) }
            .then { channel.tryEmitError(IllegalStateException("the reader lost its channel")) }
            .expectErrorMessage("the reader lost its channel")
            .verify(Duration.ofSeconds(10))
    }

    /**
     * An older reader that fails must not evict a newer entry.
     *
     * The interleaving cannot be produced through the public API, because a
     * reader only loses its entry by failing. The reflection call below puts
     * the map in the state the race produces: an older reader still runs
     * while the map already holds a newer entry. See CHAT-czmjffen.
     */
    @Test
    fun `a failed reader does not evict a newer entry`() {
        val topic = UUID.randomUUID()
        val older = Sinks.many().multicast().onBackpressureBuffer<Message<UUID, String>>()
        val newer = Sinks.many().multicast().onBackpressureBuffer<Message<UUID, String>>()
        val handed = AtomicInteger()
        val service = serviceReading {
            if (handed.getAndIncrement() == 0) older.asFlux() else newer.asFlux()
        }

        service.open(topic).block(Duration.ofSeconds(10))
        sourcesOf(service).remove(topic)
        service.open(topic).block(Duration.ofSeconds(10))

        val newerEntry = requireNotNull(sourcesOf(service)[topic])
        assertThat(handed.get()).describedAs("two readers started").isEqualTo(2)

        older.tryEmitError(IllegalStateException("the older reader lost its channel"))

        assertThat(sourcesOf(service)[topic])
            .describedAs("the newer entry survives the older failure")
            .isSameAs(newerEntry)

        val message = Message.create(
            MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), topic),
            "after-older-failure",
            true,
        )

        StepVerifier.create(service.listenTo(topic))
            .then { newer.tryEmitNext(message) }
            .assertNext { actual -> assertThat(actual.data).isEqualTo("after-older-failure") }
            .thenCancel()
            .verify(Duration.ofSeconds(10))
    }

    private fun serviceReading(
        channel: (UUID) -> Flux<Message<UUID, String>>,
    ): RedisTopicPubSubService<UUID, String> = RedisTopicPubSubService(
        KeyConfigurationPubSub(
            "t_all_topics",
            "t_st_topic_",
            "t_l_user_topics_",
            "t_l_topic_users_"
        ),
        redisTemplates.stringTemplate(),
        redisTemplates.stringMessageTemplate(),
        UUIDUtil(),
    ) { topic -> Mono.just(channel(topic)) }

    @Suppress("UNCHECKED_CAST")
    private fun sourcesOf(
        service: RedisTopicPubSubService<UUID, String>,
    ): MutableMap<UUID, Mono<Disposable>> {
        val field = service.javaClass.getDeclaredField("sources").apply { isAccessible = true }
        return field.get(service) as MutableMap<UUID, Mono<Disposable>>
    }

    @Suppress("UNCHECKED_CAST")
    private fun activeDisposable(fieldName: String, topic: UUID): Disposable {
        // Read the private cache to assert disposal of the actual subscription.
        val field = messaging.javaClass.getDeclaredField(fieldName).apply {
            isAccessible = true
        }
        val readers = field.get(messaging) as Map<UUID, Mono<Disposable>>
        return requireNotNull(readers[topic]).block(Duration.ofSeconds(10))!!
    }
}

class PubSubBeanConfiguration {
    @Bean
    fun pubsubTests(configRedisTemplate: RedisTemplateConfiguration) =
        RedisTopicPubSubService(
            KeyConfigurationPubSub(
                "t_all_topics",
                "t_st_topic_",
                "t_l_user_topics_",
                "t_l_topic_users_"
            ),
            configRedisTemplate.stringTemplate(),
            configRedisTemplate.stringMessageTemplate(),
            UUIDUtil()
        )
}
