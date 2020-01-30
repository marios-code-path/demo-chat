package com.demo.chatevents.tests

import com.demo.chatevents.config.ConfigurationTopicRedis
import com.demo.chatevents.service.KeyConfigurationPubSub
import com.demo.chatevents.service.TopicMessagingServiceRedisPubSub
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
import redis.embedded.RedisServer
import java.io.File

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageTopicMessagingServiceRedisPubSubTests : MessageTopicMessagingServiceTestBase() {

    private lateinit var redisServer: RedisServer

    private lateinit var lettuce: LettuceConnectionFactory

    private lateinit var redisTemplateServiceConfigTopicRedis: ConfigurationTopicRedis

    @BeforeAll
    fun setUp() {
        redisServer = RedisServer(File("/usr/local/bin/redis-server"), TestConfigurationPropertiesRedis.port)

        redisServer.start()

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration(TestConfigurationPropertiesRedis.host, TestConfigurationPropertiesRedis.port))

        lettuce.afterPropertiesSet()

        redisTemplateServiceConfigTopicRedis = ConfigurationTopicRedis(lettuce, mapper)

        topicService = TopicMessagingServiceRedisPubSub(
                KeyConfigurationPubSub("t_all_topics",
                        "t_st_topic_",
                        "t_l_user_topics_",
                        "t_l_topic_users_"),
                redisTemplateServiceConfigTopicRedis.stringTemplate(),
                redisTemplateServiceConfigTopicRedis.stringMessageTemplate(),
                StringUUIDKeyDecoder(),
                UUIDKeyStringEncoder()
        )

        Hooks.onOperatorDebug()
    }

    @BeforeEach
    fun tearUp() {
        redisTemplateServiceConfigTopicRedis.objectTemplate()
                .connectionFactory.reactiveConnection
                .serverCommands().flushAll().block()
    }

    @AfterAll
    fun tearDown() = redisServer.stop()
}