package com.demo.chatevents

import com.demo.chat.domain.*
import com.demo.chat.service.ChatFeedService
import com.demo.chat.service.ChatService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import redis.embedded.RedisServer
import java.time.Instant
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

@ExtendWith(SpringExtension::class)
@DataRedisTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatEventsServiceCohesionTests {

    private val port = 6777

    private lateinit var redisServer: RedisServer

    @MockBean
    lateinit var chatBackendService: ChatService<Room<RoomKey>, User<UserKey>, TextMessage>

    lateinit var redisTemplate: ReactiveStringRedisTemplate

    lateinit var lettuce: LettuceConnectionFactory

    lateinit var feedService: ChatFeedService

    @BeforeEach
    fun setUp() {
        redisServer = RedisServer(port)

        redisServer.start()

        lettuce = LettuceConnectionFactory(RedisStandaloneConfiguration("127.0.0.1", port))

        lettuce.afterPropertiesSet()

        redisTemplate = ReactiveStringRedisTemplate(lettuce) //RedisDemoConfig().stringCache(lettuce)    }

        feedService = RedisChatFeedService(redisTemplate, chatBackendService)
    }

    @AfterEach
    fun tearDown() = redisServer.stop()

    val NUM_ROOMS = 2L


    val rooms: List<UUID> = Stream
            .generate {
                UUID.randomUUID()
            }
            .limit(NUM_ROOMS)
            .collect(Collectors.toList())

    fun randomRoomId() =
            rooms[Random(NUM_ROOMS).nextInt()]


    fun randomText() =
            "Text ${Random().nextLong()}"


    @Test
    fun `test should connect to service streaming session`() {
        val userId = UUID.randomUUID()

        val userFeed = feedService.getFeedForUser(userId)

        val pushed = feedService
                .sendMessageToFeed(TestTextMessage(
                        TestTextMessageKey(
                                UUID.randomUUID(),
                                userId,
                                randomRoomId(),
                                Instant.now()
                        ),
                        randomText(),
                        true
                ))
                .repeat(2)

        val stream = Flux.from(pushed).thenMany(userFeed)

        StepVerifier
                .create(stream)
                .expectSubscription()
                .assertNext {
                    when (it) {
                        is TextMessage -> textMessageAssertion(it)
                        else -> {
                            throw Exception("This is not a proper Message<MessageKey, Any> !")
                        }
                    }


                }
                .expectComplete()
                .verify()
    }

    fun `test should join room and receive stats`() {

    }
}

/**
stream api:
joinRoom:
service.getRoomStats0,
service.join
events.listen

leaveRoom:
service.leave
events.disconnect

getEvents* functions:
eventsForRoom(userId, roomId, offset)
[service.getMessages(userId, roomId, offset),
service.getRoomStats]
events.pushMessages

pushEvent:
x -> messages
service.storeMessage x
events.pushMessage(userId, roomId, x)
 **/