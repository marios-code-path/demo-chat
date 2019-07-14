package com.demo.chatevents.tests

import com.demo.chat.service.ChatTopicServiceAdmin
import com.demo.chatevents.config.TopicRedisTemplateConfiguration
import com.demo.chatevents.service.KeyConfigurationPubSub
import com.demo.chatevents.service.TopicServiceRedisPubSub
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
import redis.embedded.RedisServer

@ExtendWith(SpringExtension::class)
@Import(TopicRedisTemplateConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TopicServiceRedisPubSubTests : TopicServiceTestBase() {

    private val port = 6379

    private lateinit var redisServer: RedisServer

    private lateinit var lettuce: LettuceConnectionFactory

    private lateinit var redisTemplateServiceConfig: TopicRedisTemplateConfiguration

    @BeforeAll
    fun setUp() {
        //  redisServer = RedisServer(File("/usr/local/bin/redis-server"), port)

        //  redisServer.start()

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port))

        lettuce.afterPropertiesSet()

        redisTemplateServiceConfig = TopicRedisTemplateConfiguration()

        topicService = TopicServiceRedisPubSub(
                KeyConfigurationPubSub("all_topics",
                        "st_topic_",
                        "l_user_topics_",
                        "l_topic_users_"),
                ReactiveStringRedisTemplate(lettuce),
                redisTemplateServiceConfig.topicTemplate(lettuce)
        )

        topicAdmin = topicService as ChatTopicServiceAdmin

        Hooks.onOperatorDebug()
    }

    @BeforeEach
    fun tearUp() {
        redisTemplateServiceConfig.objectTemplate(lettuce)
                .connectionFactory.reactiveConnection
                .serverCommands().flushAll().block()
    }

    // @AfterAll
    // fun tearDown() = redisServer.stop()

}