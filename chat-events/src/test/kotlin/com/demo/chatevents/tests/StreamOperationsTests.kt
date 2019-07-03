package com.demo.chatevents.tests

import com.demo.chatevents.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.hash.Jackson2HashMapper
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
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
import java.util.stream.Collectors

// TODO: Object Hashmap
@ExtendWith(SpringExtension::class)
@Import(ChatEventsRedisConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamOperationsTests {

    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    private val port = 6379

    private lateinit var redisServer: RedisServer

    private lateinit var objectMapper: ObjectMapper

    private lateinit var lettuce: LettuceConnectionFactory

    private lateinit var template: ReactiveRedisTemplate<String, String>

    private lateinit var msgTemplate: ReactiveRedisTemplate<String, TopicData>

    private lateinit var objTemplate: ReactiveRedisTemplate<String, Object>

    private lateinit var zoomTemplate: ReactiveRedisTemplate<String, Zoom>

    private lateinit var mapper: Jackson2HashMapper

    @BeforeAll
    fun setupRedis() {
        redisServer = RedisServer(File("/usr/local/bin/redis-server"), port)

        redisServer.start()

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port))

        lettuce.afterPropertiesSet()

        template = ReactiveStringRedisTemplate(lettuce)

        val config = ChatEventsRedisConfiguration()

        objectMapper = config.objectMapper()

        msgTemplate = config.topicTemplate(lettuce)

        objTemplate = config.objectTemplate(lettuce)

        zoomTemplate = zoomTemplate(lettuce, objectMapper)

        mapper = Jackson2HashMapper(objectMapper, true)

        Hooks.onOperatorDebug()
    }

    fun zoomTemplate(cf: ReactiveRedisConnectionFactory, objMapper: ObjectMapper): ReactiveRedisTemplate<String, Zoom> {
        val keys = StringRedisSerializer()

        val builder: RedisSerializationContext.RedisSerializationContextBuilder<String, Zoom> =
                RedisSerializationContext.newSerializationContext(keys)

        val defaultSerializer = Jackson2JsonRedisSerializer(Zoom::class.java)

        builder.key(keys)
        builder.hashKey(keys)
        builder.value(defaultSerializer)
        builder.hashValue(defaultSerializer)

        return ReactiveRedisTemplate(cf, builder.build())
    }

    @AfterAll
    fun tearDown() = redisServer.stop()


    fun `test a hash mapping values`() {
        val zoom = Zoom("foo")

        val write = zoomTemplate
                .opsForHash<String, Any>().putAll("test", mapper.toHash(zoom))

        val read = zoomTemplate
                .opsForHash<String, Any>().entries("test")
                .collectMap({ it.key }, { it.value })

        StepVerifier
                .create(
                        Flux
                                .from(write)
                                .thenMany(read)
                )
                .expectSubscription()
                .assertNext {
                    val newZoom: Zoom = mapper.fromHash(it) as Zoom

                    logger.info("New Zoom Object : $newZoom")
                }
                .verifyComplete()

    }

    @Test
    fun `should publish and receive MapRecord of TopicData`() {
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
                                .opsForStream<String, TestTopicData>()
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

    fun `should publish and receive ObjectRecord of TopicData`() {
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

        val objRecord = StreamRecords.newRecord()
                .`in`(testStreamKey)
                .ofObject(TopicData(message))
                .withId(recordId)

        val sendStream = msgTemplate
                .opsForStream<String, TopicData>(Jackson2HashMapper(true))
                .add(objRecord)
                .checkpoint("send")

        val receiveStream = msgTemplate
                .opsForStream<String, Any>(Jackson2HashMapper(true))
                .read(StreamOffset.fromStart(testStreamKey))
                .map {
                    //it.toObjectRecord<TopicData>(Jackson2HashMapper(true))
                    Jackson2HashMapper(true).fromHash(it.value)
                }
                .checkpoint("receive")
/*
	@SuppressWarnings({ "unchecked", "rawtypes" })
	default <OV> ObjectRecord<S, OV> toObjectRecord(HashMapper<? super OV, ? super K, ? super V> mapper) {
		return Record.<S, OV> of((OV) mapper.fromHash((Map) getValue())).withId(getId()).withStreamKey(getStream());
	}
 */
        val testStream = Flux.from(sendStream)
                .thenMany(receiveStream)

        StepVerifier
                .create(testStream)
                .expectSubscription()
                .assertNext {

                    val data: TopicData = it as TopicData

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