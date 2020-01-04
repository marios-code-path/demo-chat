package com.demo.chatevents.tests

import com.demo.chatevents.config.ConfigurationTopicRedis
import com.demo.chatevents.service.KeyConfigurationPubSub
import com.demo.chatevents.service.TopicServiceRedisPubSub
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
import redis.embedded.RedisServer
import java.io.File

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageTopicServiceRedisPubSubTests : MessageTopicServiceTestBase() {

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

        topicService = TopicServiceRedisPubSub(
                KeyConfigurationPubSub("t_all_topics",
                        "t_st_topic_",
                        "t_l_user_topics_",
                        "t_l_topic_users_"),
                ReactiveStringRedisTemplate(lettuce),
                redisTemplateServiceConfigTopicRedis.topicTemplate(lettuce),
                StringKeyDecoder(),
                KeyStringEncoder()
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