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
import org.springframework.data.redis.connection.stream.RecordId
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

@Testcontainers
@Extensions(
    ExtendWith(SpringExtension::class)
)
@Import(TestContextConfiguration::class, XStreamBeanConfiguration::class)
@Tag("integration")
class XStreamPubSubTests(
    @Autowired pubsub: TopicPubSubService<UUID, String>,
    @Autowired private val redisTemplates: RedisTemplateConfiguration,
) : PubSubTests<UUID, String>(pubsub, TestUUIDKeyService(), Supplier { "Test " }) {

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
                val reader = activeDisposable("topicReaders", topic)
                messaging.close(topic).block(Duration.ofSeconds(10))
                assertThat(reader.isDisposed).isTrue()
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
        val stream = Sinks.many().multicast().onBackpressureBuffer<Message<UUID, String>>()
        val service = serviceReading { stream.asFlux() }

        StepVerifier.create(service.listenTo(topic))
            .then { service.open(topic).block(Duration.ofSeconds(10)) }
            .then { stream.tryEmitError(IllegalStateException("the reader lost its stream")) }
            .expectErrorMessage("the reader lost its stream")
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

        older.tryEmitError(IllegalStateException("the older reader lost its stream"))

        assertThat(entriesOf(service)[topic])
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

    /**
     * A startup failure must reach the listeners too.
     *
     * The cursor read can fail before the stream is subscribed. That error
     * never reaches the reader handler, so it used to remove the entry and
     * leave the sink open, and a listener waited forever. The seam throws
     * here, which fails the startup Mono on the same path a failed
     * `reverseRange` would. See CHAT-czmjffen.
     */
    @Test
    fun `a startup failure ends the listener with an error`() {
        val topic = UUID.randomUUID()
        val service = serviceReading { throw IllegalStateException("the cursor read failed") }

        StepVerifier.create(service.listenTo(topic))
            .then {
                val failure = runCatching { service.open(topic).block(Duration.ofSeconds(10)) }
                assertThat(failure.exceptionOrNull())
                    .describedAs("open still reports the failure to its caller")
                    .hasMessageContaining("the cursor read failed")
            }
            .expectErrorMessage("the cursor read failed")
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
            MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), topic),
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
        records: (String) -> Flux<Message<UUID, String>>,
    ): XStreamTopicPubSubService<UUID, String> = XStreamTopicPubSubService(
        KeyConfiguration(
            "all_topics",
            "st_topic_",
            "l_user_topics_",
            "l_topic_users_"
        ),
        redisTemplates.stringTemplate(),
        redisTemplates.stringMessageTemplate(),
        StringUUIDKeyConverter(),
        UUIDKeyStringConverter(),
    ) { streamKey: String, _: RecordId -> records(streamKey) }

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
