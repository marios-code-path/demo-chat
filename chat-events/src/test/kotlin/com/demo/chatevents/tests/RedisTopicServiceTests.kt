package com.demo.chatevents.tests

import com.demo.chat.domain.ChatException
import com.demo.chat.service.ChatTopicService
import com.demo.chatevents.ChatEventsRedisConfiguration
import com.demo.chatevents.KeyConfiguration
import com.demo.chatevents.TopicServiceRedis
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier
import redis.embedded.RedisServer
import java.util.*

@ExtendWith(SpringExtension::class)
@Import(ChatEventsRedisConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisTopicServiceTests {
    private val logger = LoggerFactory.getLogger(this::class.simpleName)

    private val port = 6379

    private lateinit var redisServer: RedisServer

    private lateinit var lettuce: LettuceConnectionFactory

    private lateinit var topicService: ChatTopicService

    private lateinit var redisServiceConfig: ChatEventsRedisConfiguration

    @BeforeAll
    fun setupRedis() {
        //redisServer = RedisServer(File("/usr/local/bin/redis-server"), port)

        //redisServer.start()

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port))

        lettuce.afterPropertiesSet()

        redisServiceConfig = ChatEventsRedisConfiguration()

        topicService = TopicServiceRedis(
                KeyConfiguration("all_topics",
                        "st_topic_",
                        "l_user_topics_",
                        "l_topic_users_"),
                ReactiveStringRedisTemplate(lettuce),
                redisServiceConfig.topicTemplate(lettuce)
        )

        Hooks.onOperatorDebug()
    }

    //@AfterAll
    //fun tearDown() = redisServer.stop()

    @Test
    fun `should create a topic`() {
        val topicId = UUID.randomUUID()

        val stream = topicService
                .createTopic(topicId)
                .then(topicService.topicExists(topicId))

        StepVerifier
                .create(stream)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .`as`("topic created, exists")
                            .isTrue()
                }
                .verifyComplete()
    }

    @Test
    fun `should get error of non-existent topic`() {
        val userId = UUID.randomUUID()
        val topicId = UUID.randomUUID()

        val stream = topicService.subscribeToTopic(userId, topicId)

        StepVerifier
                .create(stream)
                .expectSubscription()
                .expectError(ChatException::class.java)
                .verify()
    }

    @Test
    fun `should create, subscribe and unsubscribe to a topic`() {
        val userId = UUID.randomUUID()
        val topicId = UUID.randomUUID()

        val createTopic = topicService
                .createTopic(topicId)

        val subscriber = topicService
                .subscribeToTopic(userId, topicId)

        val unsubscribe = topicService
                .unSubscribeFromTopic(userId, topicId)

        val stream = Flux
                .from(createTopic)
                .then(subscriber)
                .then(unsubscribe)

        StepVerifier
                .create(stream)
                .expectSubscription()
                .verifyComplete()
    }

}