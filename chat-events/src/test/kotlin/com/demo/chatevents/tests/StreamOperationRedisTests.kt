package com.demo.chatevents.tests

import com.demo.chat.domain.Message
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.UUIDTopicMessageKey
import com.demo.chatevents.*
import com.demo.chatevents.config.ConfigurationPropertiesTopicRedis
import com.demo.chatevents.config.ConfigurationTopicRedis
import com.demo.chatevents.service.TopicManager
import com.demo.chatevents.topic.TopicData
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import reactor.test.scheduler.VirtualTimeScheduler
import redis.embedded.RedisServer
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Supplier

// TODO: Object Hashmap
// TODO: These tests currently do not execute without fail 100% of the time!
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamOperationRedisTests {

    object ConfigProps : ConfigurationPropertiesTopicRedis {
        override val port: Int = 6374
        override val host: String = "127.0.0.1"
    }

    private val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    private val streamManager = TopicManager()

    private lateinit var redisServer: RedisServer

    private lateinit var objectMapper: ObjectMapper

    private lateinit var lettuce: LettuceConnectionFactory

    private lateinit var template: ReactiveRedisTemplate<String, String>

    private lateinit var msgTemplate: ReactiveRedisTemplate<String, TopicData>

    private lateinit var objTemplate: ReactiveRedisTemplate<String, Object>

    private lateinit var testEntityTemplate: ReactiveRedisTemplate<String, TestEntity>

    private lateinit var mapper: Jackson2HashMapper

    @BeforeAll
    fun setupRedis() {
        redisServer = RedisServer(File("/usr/local/bin/redis-server"), ConfigProps.port)

        redisServer.start()

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration(ConfigProps.host, ConfigProps.port))

        lettuce.afterPropertiesSet()

        template = ReactiveStringRedisTemplate(lettuce)

        val config = ConfigurationTopicRedis(ConfigProps)

        objectMapper = config.objectMapper()

        msgTemplate = config.topicTemplate(lettuce)

        objTemplate = config.objectTemplate(lettuce)

        testEntityTemplate = testTemplate(lettuce, objectMapper)

        mapper = Jackson2HashMapper(objectMapper, true)

        Hooks.onOperatorDebug()
    }

    @BeforeEach
    fun tearUp() {
        objTemplate
                .connectionFactory.reactiveConnection
                .serverCommands().flushAll().block()
    }

    @AfterAll
    fun tearDown() = redisServer.stop()

    fun testTemplate(cf: ReactiveRedisConnectionFactory, objMapper: ObjectMapper): ReactiveRedisTemplate<String, TestEntity> {
        val keys = StringRedisSerializer()

        val builder: RedisSerializationContext.RedisSerializationContextBuilder<String, TestEntity> =
                RedisSerializationContext.newSerializationContext(keys)

        val defaultSerializer = Jackson2JsonRedisSerializer(TestEntity::class.java)

        builder.key(keys)
        builder.hashKey(keys)
        builder.value(defaultSerializer)
        builder.hashValue(defaultSerializer)

        return ReactiveRedisTemplate(cf, builder.build())
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


    fun `test a hash mapping values`() {
        val zoom = TestEntity("foo")

        val write = testEntityTemplate
                .opsForHash<String, Any>().putAll("test", mapper.toHash(zoom))

        val read = testEntityTemplate
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
                    val newTestEntity: TestEntity = mapper.fromHash(it) as TestEntity

                    logger.info("New TestEntity Object : $newTestEntity")
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
                        TestUserMessageKey(testEventId, testUserId, testRoomId, Instant.now()),
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
                TestUserMessageKey(testEventId, testUserId, testRoomId, Instant.now()),
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
                Pair("id", testUserId),
                Pair("value", "HELLO TEXT TEST"),
                Pair("type", "TextMessage"))

        val recordId = RecordId.autoGenerate()

        val sendStream = template
                .opsForStream<String, String>()
                .add(MapRecord
                        .create(testStreamKey, map)
                        .withId(recordId))

        val receiveStream = template
                .opsForStream<String, String>()
                .read(StreamOffset.fromStart(testStreamKey))

        StepVerifier
                .create(sendStream)
                .expectSubscription()
                .expectNextCount(1)
                .verifyComplete()

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
                Pair("id", testUserId),
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
    fun `stream manager can handle redis streams`() {
        val room = testRoomId()
        val user = testUserId()

        objTemplate
                .connectionFactory.reactiveConnection
                .serverCommands().flushAll().block()

        val xread = msgTemplate
                .opsForStream<String, TopicData>()
                .read(StreamOffset.fromStart(room.toString()))
                .map {
                    it.value["data"]?.state!!
                }


        val xsend = { x: String ->
            msgTemplate
                    .opsForStream<String, TopicData>()
                    .add(MapRecord
                            .create(room.toString(), mapOf(Pair("data", TopicData(
                                    TextMessage.create(UUID.randomUUID(), room, user, x)
                            ))))
                            .withId(RecordId.autoGenerate()))
                    .then()
        }

        val userFlux = streamManager.getTopicFlux(user)

        streamManager.subscribeTopic(room, user)

        streamManager.subscribeTopicProcessor(room, xread)

        StepVerifier
                .create(xsend("Hello1"))
                .verifyComplete()

        StepVerifier
                .create(xsend("Hello2"))
                .verifyComplete()

        StepVerifier
                .create(userFlux)
                .then {
                    StepVerifier.create(xread)
                            .expectNextCount(2)
                            .verifyComplete()
                }
                .expectNextCount(2)
                .then {
                    streamManager.quitTopic(room, user)
                    streamManager.closeTopic(user)
                }

    }

    @Test
    fun `Test Processor should feed from Xread Command`() {
        val room = testRoomId()
        val user = testUserId()

        val xread = msgTemplate
                .opsForStream<String, TopicData>()
                .read(StreamOffset.fromStart(room.toString()))
                .map {
                    it.value["data"]?.state!!
                }

        val xsend = { x: String ->
            msgTemplate
                    .opsForStream<String, TopicData>()
                    .add(MapRecord
                            .create(room.toString(), mapOf(Pair("data", TopicData(
                                    TextMessage.create(UUID.randomUUID(), room, user, x)
                            ))))
                            .withId(RecordId.autoGenerate()))
                    .then()
        }

        StepVerifier
                .create(xsend("HELLO1"))
                .expectSubscription()
                .verifyComplete()

        StepVerifier
                .create(xsend("HELLO2"))
                .expectSubscription()
                .verifyComplete()

        val testProcessor: TestPublisher<Message<UUIDTopicMessageKey, Any>> =
                TestPublisher.create()

        val tpDisposable = xread
                .subscribe {
                    testProcessor.next(it)
                }

        val tpFlux = testProcessor
                .flux()
                .doOnNext { logger.info("TP : ${it.data}") }

        StepVerifier
                .create(tpFlux)
                .then {
                    StepVerifier.create(xread)
                            .expectNextCount(2)
                            .verifyComplete()
                }
                .expectNextCount(2)
                .then {
                    tpDisposable.dispose()
                    streamManager.quitTopic(room, user)
                    streamManager.closeTopic(user)
                    testProcessor.complete()
                }
                .expectComplete()
                .verify(Duration.ofSeconds(2))

    }
}