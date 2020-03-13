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

    private lateinit var configTopicRedis: ConfigurationTopicRedis

    @BeforeAll
    fun setUp() {
        redisServer = RedisServer(File("/usr/local/bin/redis-server"), TestConfigurationPropertiesRedisCluster.port)

        redisServer.start()

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration(TestConfigurationPropertiesRedisCluster.host, TestConfigurationPropertiesRedisCluster.port))

        lettuce.afterPropertiesSet()

        configTopicRedis = ConfigurationTopicRedis(lettuce, mapper)

        topicService = TopicMessagingServiceRedisStream(
                KeyConfiguration("all_topics",
                        "st_topic_",
                        "l_user_topics_",
                        "l_topic_users_"),
                configTopicRedis.stringTemplate(),
                configTopicRedis.stringMessageTemplate(),
                StringUUIDKeyDecoder(),
                UUIDKeyStringEncoder(),
                KeyRecordIdEncoder()
        )

        Hooks.onOperatorDebug()
    }

    @BeforeEach
    fun tearUp() {
        configTopicRedis.anyTemplate()
                .connectionFactory.reactiveConnection
                .serverCommands().flushAll().block()
    }

    @AfterAll
    fun tearDown() = redisServer.stop()
}