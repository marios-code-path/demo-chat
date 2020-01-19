package com.demo.chatevents.tests

import com.demo.chat.codec.Codec
import com.demo.chatevents.config.ConfigurationTopicRedis
import com.demo.chatevents.service.KeyConfiguration
import com.demo.chatevents.service.TopicMessagingServiceRedisStream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
import redis.embedded.RedisServer
import java.io.File
import java.util.*

class KeyRecordIdEncoder<T> : Codec<T, RecordId> {
    override fun decode(record: T): RecordId =
            when (record) {
                is UUID -> if ((record.mostSignificantBits) == 0L)
                    RecordId.autoGenerate()
                else
                    RecordId.of(record.mostSignificantBits, record.leastSignificantBits)
                else -> RecordId.autoGenerate()
            }
}

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageTopicMessagingServiceRedisStreamTests : MessageTopicMessagingServiceTestBase() {

    private lateinit var redisServer: RedisServer

    private lateinit var lettuce: LettuceConnectionFactory

    private lateinit var redisTemplateServiceConfigTopicRedis: ConfigurationTopicRedis

    @BeforeAll
    fun setUp() {
        redisServer = RedisServer(File("/usr/local/bin/redis-server"), configProps.port)

        redisServer.start()

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration(configProps.host, configProps.port))

        lettuce.afterPropertiesSet()

        redisTemplateServiceConfigTopicRedis = ConfigurationTopicRedis(configProps)

        topicService = TopicMessagingServiceRedisStream(
                KeyConfiguration("all_topics",
                        "st_topic_",
                        "l_user_topics_",
                        "l_topic_users_"),
                ReactiveStringRedisTemplate(lettuce),
                redisTemplateServiceConfigTopicRedis.stringMessageTemplate(lettuce),
                StringUUIDKeyDecoder(),
                UUIDKeyStringEncoder(),
                KeyRecordIdEncoder()
        )

        Hooks.onOperatorDebug()
    }

    @BeforeEach
    fun tearUp() {
        redisTemplateServiceConfigTopicRedis.objectTemplate(lettuce)
                .connectionFactory.reactiveConnection
                .serverCommands().flushAll().block()
    }

    @AfterAll
    fun tearDown() = redisServer.stop()
}