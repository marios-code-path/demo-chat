package com.demo.chatevents.tests

import com.demo.chatevents.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier
import reactor.test.scheduler.VirtualTimeScheduler
import redis.embedded.RedisServer
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Supplier

@ExtendWith(SpringExtension::class)
@Import(ChatEventsRedisConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamOperationsTests {

    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val port = 6379

    private lateinit var redisServer: RedisServer

    private lateinit var lettuce: LettuceConnectionFactory

    private lateinit var template: ReactiveRedisTemplate<String, String>

    private lateinit var msgTemplate: ReactiveRedisTemplate<String, TopicData>

    @BeforeAll
    fun setupRedis() {
        redisServer = RedisServer(File("/usr/local/bin/redis-server"), port)

        redisServer.start()

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port))

        lettuce.afterPropertiesSet()

        template = ReactiveStringRedisTemplate(lettuce)

        msgTemplate = ChatEventsRedisConfiguration().someCache(lettuce)

        Hooks.onOperatorDebug()
    }

    @AfterAll
    fun tearDown() = redisServer.stop()

    @Test
    fun `should publish and receive topic data`() {
        val testStreamKey = "TEST_STREAM_" + UUID.randomUUID().toString()

        val notMessages = Supplier {

            val messages = Flux.generate<TestTextMessage> {
                val testRoomId = UUID.randomUUID()
                val testEventId = UUID.randomUUID()
                val testUserId = UUID.randomUUID()

                it.next(TestTextMessage(
                        TestTextMessageKey(testEventId, testUserId, testRoomId, Instant.now()),
                        "TEST ${randomText()}",
                        true
                ))
            }
                    .take(1)

            val sender = Flux
                    .from(messages)
                    .flatMap {
                        val recordId = RecordId.autoGenerate()
                        val dataMap = mapOf(Pair("data", TopicData(it)))

                        msgTemplate
                                .opsForStream<String, TopicData>()
                                .add(MapRecord
                                        .create(testStreamKey, dataMap)
                                        .withId(recordId))
                                .checkpoint("Send")
                    }

            val receiver = msgTemplate
                    .opsForStream<String, TopicData>()
                    .read(StreamOffset.fromStart(testStreamKey))
                    .map {
                        it.value
                    }
                    .checkpoint("receive")

            Flux
                    .from(sender)
                    .thenMany(receiver)
        }

        VirtualTimeScheduler.getOrSet()
        StepVerifier
                .withVirtualTime(notMessages)
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(1))
                .assertNext {
                    Assertions
                            .assertThat(it["data"])
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrProperty("state")

                    val data = it["data"]?.state

                    Assertions
                            .assertThat(data)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrProperty("key")
                            .hasFieldOrProperty("value")
                            .hasFieldOrPropertyWithValue("visible", true)
                }
                .verifyComplete()

    }

    @Test
    fun `should deserialize from json to a valid message type`() {
        val testStreamKey = "TEST_STREAM_" + UUID.randomUUID().toString()
        val testRoomId = UUID.randomUUID()
        val testEventId = UUID.randomUUID()
        val testUserId = UUID.randomUUID()

        val message = TestTextMessage(
                TestTextMessageKey(testEventId, testUserId, testRoomId, Instant.now()),
                "TEST MESSAGE",
                true
        )

        val recordId = RecordId.autoGenerate()

        val map = mapOf(Pair("data", TopicData(message)))

        val sendStream = msgTemplate
                .opsForStream<String, TopicData>()
                .add(MapRecord
                        .create(testStreamKey, map)
                        .withId(recordId))
                .checkpoint("Send")

        val receiveStream = msgTemplate
                .opsForStream<String, TopicData>()
                .read(StreamOffset.fromStart(testStreamKey))
                .map {
                    it.value
                }
                .checkpoint("receive")

        val testStream = Flux.from(sendStream)
                .thenMany(receiveStream)

        StepVerifier
                .create(testStream)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it["data"])
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrProperty("state")

                    val data = it["data"]?.state

                    Assertions
                            .assertThat(data)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrProperty("key")
                            .hasFieldOrPropertyWithValue("value", "TEST MESSAGE")
                            .hasFieldOrPropertyWithValue("visible", true)
                }
                .verifyComplete()
    }

    @Test
    fun `should listener receive emitted record`() {
        val testStreamKey = "TEST_STREAM_" + UUID.randomUUID().toString()
        val testRoomId = UUID.randomUUID()
        val testEventId = UUID.randomUUID()
        val testUserId = UUID.randomUUID()

        val map = mapOf(Pair("msgId", testEventId),
                Pair("visible", true),
                Pair("userId", testUserId),
                Pair("value", "HELLO TEXT TEST"),
                Pair("type", "TextMessage"))

        val recordId = RecordId.autoGenerate()

        val sendStream = template
                .opsForStream<String, String>()
                .add(MapRecord
                        .create(testStreamKey, map)
                        .withId(recordId))

        val receiveStream = Flux
                .from(sendStream)
                .thenMany(template
                        .opsForStream<String, String>()
                        .read(StreamOffset.fromStart(testStreamKey))
                )

        StepVerifier
                .create(receiveStream)
                .expectSubscription()
                .assertNext {
                    Assertions.assertThat(it.value["msgId"])
                            .isEqualTo(testEventId.toString())
                }
                .verifyComplete()
    }

    @Test
    fun `should emit and receive Record ID`() {
        val testStreamKey = "TEST_ROOM_"
        val testRoomId = UUID.randomUUID()
        val testEventId = UUID.randomUUID()
        val testUserId = UUID.randomUUID()

        val map = mapOf(Pair("msgId", testEventId),
                Pair("visible", true),
                Pair("userId", testUserId),
                Pair("value", "HELLO TEXT TEST"),
                Pair("type", "TextMessage"))

        val recordId = RecordId.autoGenerate()

        val sendStream = template
                .opsForStream<String, String>()
                .add(MapRecord
                        .create(testStreamKey + testRoomId, map)
                        .withId(recordId))

        StepVerifier
                .create(sendStream)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it.timestamp)
                            .isNotNull()
                            .isGreaterThan(0L)

                    Assertions
                            .assertThat(it.sequence)
                            .isNotNull()
                            .isGreaterThanOrEqualTo(0L)
                    logger.info("Is there something else ${it.timestamp}-${it.sequence}")
                }
                .verifyComplete()
    }

    @Test
    fun testShouldPing() {

        val ping = template.connectionFactory.reactiveConnection.ping()

        StepVerifier
                .create(ping)
                .expectSubscription()
                .expectNext("PONG")
                .verifyComplete()
    }
}