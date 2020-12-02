package com.demo.chat.test.messaging

import com.demo.chat.codec.Decoder
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.service.impl.memory.messaging.KeyConfiguration
import com.demo.chat.service.impl.memory.messaging.PubSubTopicExchangeRedisStream
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
import redis.embedded.RedisServer
import java.io.File
import java.util.*

class KeyRecordIdEncoder<T> : Decoder<T, RecordId> {
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

    private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

    private lateinit var redisServer: RedisServer

    private lateinit var lettuce: LettuceConnectionFactory

    private lateinit var configRedisTemplate: RedisTemplateConfiguration

    @BeforeAll
    fun setUp() {
        try {
            logger.debug("Starting a Redis Server ${redisPath} on ${TestConfigurationPropertiesRedisCluster.port}")
            redisServer = RedisServer(File(redisPath), TestConfigurationPropertiesRedisCluster.port)
            redisServer.start()
        } catch (e: Throwable){
            logger.error("Redis Service failed with: ${e.message}")
        }

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration(TestConfigurationPropertiesRedisCluster.host, TestConfigurationPropertiesRedisCluster.port))

        lettuce.afterPropertiesSet()

        configRedisTemplate = RedisTemplateConfiguration(lettuce, mapper)

        topicService = PubSubTopicExchangeRedisStream(
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
    fun tearDown() {
        if (redisServer.isActive)
            redisServer.stop()
    }
}