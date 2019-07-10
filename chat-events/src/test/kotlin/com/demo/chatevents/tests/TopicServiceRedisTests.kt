package com.demo.chatevents.tests

import com.demo.chat.domain.JoinAlert
import com.demo.chat.service.ChatTopicServiceAdmin
import com.demo.chatevents.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.ReplayProcessor
import reactor.test.StepVerifier
import redis.embedded.RedisServer
import java.time.Duration
import java.util.*

@ExtendWith(SpringExtension::class)
@Import(ChatEventsRedisConfiguration::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TopicServiceRedisTests : TopicServiceTestBase() {

    private val port = 6379

    private lateinit var redisServer: RedisServer

    private lateinit var lettuce: LettuceConnectionFactory

    private lateinit var redisServiceConfig: ChatEventsRedisConfiguration

    @BeforeAll
    fun setUp() {
        //  redisServer = RedisServer(File("/usr/local/bin/redis-server"), port)

        //  redisServer.start()

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

        topicAdmin = topicService as ChatTopicServiceAdmin

        Hooks.onOperatorDebug()
    }

    @BeforeEach
    fun tearUp() {
        redisServiceConfig.objectTemplate(lettuce)
                .connectionFactory.reactiveConnection
                .serverCommands().flushAll().block()
    }

    // @AfterAll
    // fun tearDown() = redisServer.stop()


    @Test
    fun `test stream downstream subscriber count`() {
        val mySource = Flux.just("A","B","C")
                .doOnNext {
                    logger.info("I Have $it")
                }

        val sourceProcessor = ReplayProcessor.create<String>(5)

        val consumerProcessor = ReplayProcessor.create<String>(5)

        val srcDisp = sourceProcessor
                .subscribe {
                    consumerProcessor.onNext(it)
                }

       val conDisp = mySource
                .subscribe {
                    sourceProcessor.onNext(it)
                }

        val xerxesTheFlux = consumerProcessor
                .onBackpressureBuffer()
                .publish()
                .autoConnect()

        val vladTheFlux = consumerProcessor
                .onBackpressureBuffer()
                .publish()
                .autoConnect()

        StepVerifier.create(Flux.merge(vladTheFlux, xerxesTheFlux))
                .expectSubscription()
                .then {

                    Assertions
                            .assertThat(consumerProcessor.downstreamCount())
                            .isEqualTo(2)

                    Assertions
                            .assertThat(sourceProcessor.downstreamCount())
                            .isEqualTo(1)

                }
                .expectNextCount(6)
                .then {
                    consumerProcessor.onComplete()
                }
                .expectComplete()
                .verify(Duration.ofSeconds(2))
    }

    @Test
    fun `should send message to created topic and verify reception`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        StepVerifier
                .create(topicService.createTopic(testRoom))
                .expectSubscription()
                .expectComplete()
                .verify(Duration.ofSeconds(1))

        StepVerifier
                .create(topicService.subscribeToTopic(userId, testRoom))
                .expectSubscription()
                .expectComplete()
                .verify(Duration.ofSeconds(1))

        StepVerifier
                .create(topicService.sendMessageToTopic(JoinAlert.create(UUID.randomUUID(), testRoom, userId)))
                .expectSubscription()
                .expectComplete()
                .verify(Duration.ofSeconds(1))

        val uFlux = topicService.receiveEvents(userId).doOnNext { logger.info("GOT: ${it.key.topicId}") }

        StepVerifier
                .create(uFlux)
                .expectSubscription()
                .then {
                    Assertions
                            .assertThat(topicAdmin.getStreamProcessor(userId).downstreamCount())
                            .isGreaterThanOrEqualTo(1)
                }
                .expectNextCount(1)
                .then {
                    StepVerifier
                            .create(Flux.merge(
                                    topicService.unSubscribeFromTopic(userId, testRoom),
                                    topicService.closeTopic(userId),
                                    topicService.closeTopic(testRoom)
                            ))
                            .expectSubscription()
                            .verifyComplete()
                }
                .expectComplete()
                .verify(Duration.ofSeconds(3))
    }
}