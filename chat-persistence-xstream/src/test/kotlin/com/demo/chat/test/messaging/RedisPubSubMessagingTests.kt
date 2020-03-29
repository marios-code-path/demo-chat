package com.demo.chat.test.messaging

import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.service.messaging.KeyConfigurationPubSub
import com.demo.chat.service.messaging.TopicMessagingServiceRedisPubSub
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
import redis.embedded.RedisServer
import java.io.File

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisPubSubMessagingTests : MessagingServiceTestBase() {
    private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

    private lateinit var redisServer: RedisServer

    private lateinit var lettuce: LettuceConnectionFactory

    private lateinit var redisTemplateServiceConfigRedisTemplate: RedisTemplateConfiguration


    @BeforeAll
    fun setUp() {
        try {
            redisServer = RedisServer(File(redisPath), TestConfigurationPropertiesRedisCluster.port)
            redisServer.start()
        } catch (e: Throwable){
            logger.error("Redis Service failed with: ${e.message} of ${redisPath} on ${TestConfigurationPropertiesRedisCluster.port}")
        }

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration(TestConfigurationPropertiesRedisCluster.host, TestConfigurationPropertiesRedisCluster.port))

        lettuce.afterPropertiesSet()

        redisTemplateServiceConfigRedisTemplate = RedisTemplateConfiguration(lettuce, mapper)

        topicService = TopicMessagingServiceRedisPubSub(
                KeyConfigurationPubSub("t_all_topics",
                        "t_st_topic_",
                        "t_l_user_topics_",
                        "t_l_topic_users_"),
                redisTemplateServiceConfigRedisTemplate.stringTemplate(),
                redisTemplateServiceConfigRedisTemplate.stringMessageTemplate(),
                StringUUIDKeyDecoder(),
                UUIDKeyStringEncoder()
        )

        Hooks.onOperatorDebug()
    }

    @BeforeEach
    fun tearUp() {
        redisTemplateServiceConfigRedisTemplate.anyTemplate()
                .connectionFactory.reactiveConnection
                .serverCommands().flushAll().block()
    }

    @AfterAll
    fun tearDown() {
        if (redisServer.isActive)
            redisServer.stop()
    }
}