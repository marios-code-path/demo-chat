package com.demo.chat.test.messaging

import com.demo.chat.test.key.TestKeys

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
            TestKeys.message(UUID.randomUUID(), UUID.randomUUID(), topic),
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
        entriesOf(service).remove(topic)
        service.open(topic).block(Duration.ofSeconds(10))

        val newerEntry = requireNotNull(entriesOf(service)[topic])
        assertThat(handed.get()).describedAs("two readers started").isEqualTo(2)

        older.tryEmitError(IllegalStateException("the older reader lost its channel"))

        assertThat(entriesOf(service)[topic])
            .describedAs("the newer entry survives the older failure")
            .isSameAs(newerEntry)

        val message = Message.create(
            TestKeys.message(UUID.randomUUID(), UUID.randomUUID(), topic),
            "after-older-failure",
            true,
        )

        StepVerifier.create(service.listenTo(topic))
            .then { newer.tryEmitNext(message) }
            .assertNext { actual -> assertThat(actual.data).isEqualTo("after-older-failure") }
            .thenCancel()
            .verify(Duration.ofSeconds(10))
    }

    /**
     * A startup failure must reach the listeners too.
     *
     * `listenToLater` can fail before any reader exists. That error never
     * reaches the reader handler, so it used to remove the entry and leave
     * the sink open, and a listener waited forever. See CHAT-czmjffen.
     */
    @Test
    fun `a startup failure ends the listener with an error`() {
        val topic = UUID.randomUUID()
        val service = RedisTopicPubSubService(
            KeyConfigurationPubSub(
                "t_all_topics",
                "t_st_topic_",
                "t_l_user_topics_",
                "t_l_topic_users_"
            ),
            redisTemplates.stringTemplate(),
            redisTemplates.stringMessageTemplate(),
            UUIDUtil(),
        ) { Mono.error(IllegalStateException("the channel never opened")) }

        StepVerifier.create(service.listenTo(topic))
            .then {
                val failure = runCatching { service.open(topic).block(Duration.ofSeconds(10)) }
                assertThat(failure.exceptionOrNull())
                    .describedAs("open still reports the failure to its caller")
                    .hasMessageContaining("the channel never opened")
            }
            .expectErrorMessage("the channel never opened")
            .verify(Duration.ofSeconds(10))
    }

    /**
     * A failed reader must not terminate the sink a newer reader adopted.
     *
     * The entry used to hold only the reader, and the sink lived in a second
     * map. A handler removed the entry, and a concurrent `open` could take
     * the old sink and install a new reader before the handler reached the
     * second removal. The handler then errored a sink the new reader was
     * already writing to, and later listeners received a sink with no reader.
     *
     * The entry carries the sink now, so the two retire together and a newer
     * reader always brings its own sink. See CHAT-czmjffen.
     */
    @Test
    fun `a failed reader does not terminate the sink of a newer reader`() {
        val topic = UUID.randomUUID()
        val older = Sinks.many().multicast().onBackpressureBuffer<Message<UUID, String>>()
        val newer = Sinks.many().multicast().onBackpressureBuffer<Message<UUID, String>>()
        val handed = AtomicInteger()
        val service = serviceReading {
            if (handed.getAndIncrement() == 0) older.asFlux() else newer.asFlux()
        }

        service.open(topic).block(Duration.ofSeconds(10))
        val olderSink = sinkIn(requireNotNull(entriesOf(service)[topic]))

        entriesOf(service).remove(topic)
        service.open(topic).block(Duration.ofSeconds(10))
        val newerSink = sinkIn(requireNotNull(entriesOf(service)[topic]))

        assertThat(newerSink)
            .describedAs("a newer reader brings its own sink")
            .isNotSameAs(olderSink)

        val message = Message.create(
            TestKeys.message(UUID.randomUUID(), UUID.randomUUID(), topic),
            "newer-sink-survives",
            true,
        )

        StepVerifier.create(service.listenTo(topic))
            .then { older.tryEmitError(IllegalStateException("the older reader failed")) }
            .then { newer.tryEmitNext(message) }
            .assertNext { actual -> assertThat(actual.data).isEqualTo("newer-sink-survives") }
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

    /** The private topic map, which now holds the sink and the reader together. */
    @Suppress("UNCHECKED_CAST")
    private fun entriesOf(service: Any): MutableMap<UUID, Any> {
        val field = service.javaClass.getDeclaredField("topics").apply { isAccessible = true }
        return field.get(service) as MutableMap<UUID, Any>
    }

    private fun sinkIn(entry: Any): Any {
        val field = entry.javaClass.getDeclaredField("sink").apply { isAccessible = true }
        return field.get(entry)
    }

    @Suppress("UNCHECKED_CAST")
    private fun activeDisposable(@Suppress("UNUSED_PARAMETER") fieldName: String, topic: UUID): Disposable {
        // Read the private entry to assert disposal of the actual subscription.
        val entry = requireNotNull(entriesOf(messaging as Any)[topic])
        val readerField = entry.javaClass.getDeclaredField("reader").apply { isAccessible = true }
        val reader = readerField.get(entry) as Mono<Disposable>
        return reader.block(Duration.ofSeconds(10))!!
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
