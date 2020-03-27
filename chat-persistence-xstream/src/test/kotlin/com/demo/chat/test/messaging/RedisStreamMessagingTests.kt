package com.demo.chat.test.messaging

import com.demo.chat.codec.Codec
import com.demo.chat.service.messaging.KeyConfiguration
import com.demo.chat.service.messaging.TopicMessagingServiceRedisStream
import com.demo.chatevents.config.ConfigurationRedisTemplate
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
class RedisStreamMessagingTests : MessagingServiceTestBase() {

    private lateinit var redisServer: RedisServer

    private lateinit var lettuce: LettuceConnectionFactory

    private lateinit var configRedisTemplate: ConfigurationRedisTemplate

    @BeforeAll
    fun setUp() {
        //redisServer = RedisServer(File("/usr/local/bin/redis-server"), com.demo.chatevents.tests.MessageTopicMessagingServiceTestBase.TestConfigurationPropertiesRedisCluster.port)
        redisServer = RedisServer(TestConfigurationPropertiesRedisCluster.port)

        redisServer.start()

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration(TestConfigurationPropertiesRedisCluster.host, TestConfigurationPropertiesRedisCluster.port))

        lettuce.afterPropertiesSet()

        configRedisTemplate = ConfigurationRedisTemplate(lettuce, mapper)

        topicService = TopicMessagingServiceRedisStream(
                KeyConfiguration("all_topics",
                        "st_topic_",
                        "l_user_topics_",
                        "l_topic_users_"),
                configRedisTemplate.stringTemplate(),
                configRedisTemplate.stringMessageTemplate(),
                StringUUIDKeyDecoder(),
                UUIDKeyStringEncoder(),
                KeyRecordIdEncoder()
        )

        Hooks.onOperatorDebug()
    }

    @BeforeEach
    fun tearUp() {
        configRedisTemplate.anyTemplate()
                .connectionFactory.reactiveConnection
                .serverCommands().flushAll().block()
    }

    @AfterAll
    fun tearDown() = redisServer.stop()
}