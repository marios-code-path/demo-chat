package com.demo.chat.deploy.test

import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.config.controller.composite.MessageServiceController
import com.demo.chat.config.controller.composite.TopicServiceController
import com.demo.chat.config.controller.composite.UserServiceController
import com.demo.chat.deploy.memory.MemoryDeploymentApp
import com.demo.chat.domain.*
import com.demo.chat.service.composite.CompositeServiceBeans
import com.demo.chat.service.core.*
import com.demo.chat.test.randomAlphaNumeric
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*


@SpringBootTest(
    classes = [MemoryDeploymentApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(
    properties = [
        "server.port=0", "spring.rsocket.server.port=0",
        "app.rootkeys.create=true",
        "app.service.core.key", "app.service.security.userdetails",
        "app.service.core.pubsub", "app.service.core.index", "app.service.core.persistence",
        "app.service.core.secrets", "app.service.composite",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.topic", "app.controller.message",
        "spring.config.location=classpath:/application.yml"
    ]
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CompositeServiceTests {

    @Autowired
    lateinit var topicService: TopicServiceController<Long, MessageTopic<Long>>

    @Autowired
    lateinit var userService: UserServiceController<Long, Map<String, String>>

    @Autowired
    lateinit var messagingService: MessageServiceController<Long, String>

    @Autowired
    private lateinit var builder: RSocketRequester.Builder

    @Value("\${local.rsocket.server.port}")
    private lateinit var port: String

    @Test
    fun portOf() {
        Assertions
            .assertThat(port.toInt())
            .isNotNull
            .isGreaterThan(0)
    }

    @Test
    fun `should request key`(
        @Autowired services: CompositeServiceBeans<Long, String>,
        @Autowired secretsStore: SecretsStoreBeans<Long>
    ) {
        val requester = builder
            .tcp("localhost", port.toInt())

        val keyRequest: Mono<Key<*>> = requester
            .route("key.key")
            .data(User::class.java)
            .retrieveMono(Key::class.java)

        StepVerifier.create(keyRequest)
            .assertNext { key ->
                Assertions
                    .assertThat(key)
                    .isNotNull
            }
            .verifyComplete()

    }

    @Test
    fun `create rooms and receive list`() {
        val requestA = ByStringRequest(randomAlphaNumeric(4) + "Room")
        val requestB = ByStringRequest(randomAlphaNumeric(4) + "Room")

        val roomStream = topicService
            .addRoom(requestA)
            .then(topicService.addRoom(requestB))
            .thenMany(topicService.listRooms())

        StepVerifier
            .create(roomStream)
            .thenConsumeWhile { it.data == requestA.name || it.data == requestB.name }
            .verifyComplete()
    }

    @Test
    fun `create room and user, join room and verify membership`() {
        Hooks.onOperatorDebug()

        val roomName = randomAlphaNumeric(4) + "Room"
        val roomCreateReq = ByStringRequest(roomName)

        val roomCreated = topicService.addRoom(roomCreateReq).doOnNext { println(" ROOM : ${it.id}") }
        val userCreated = userService.addUser(UserCreateRequest("user", "testuser", "http://"))

        val roomJoined = roomCreated
            .zipWith(userCreated)
            .flatMap { tuple ->
                topicService.joinRoom(MembershipRequest(tuple.t2.id, tuple.t1.id))
            }

        StepVerifier
            .create(roomJoined)
            .verifyComplete()

        val roomMembers = topicService
            .getRoomByName(ByStringRequest(roomName))
            .flatMapMany { topicService.roomMembers(ByIdRequest(it.key.id)) }

        StepVerifier
            .create(roomMembers)
            .assertNext { memberships ->
                Assertions
                    .assertThat(memberships)
                    .isNotNull

                Assertions
                    .assertThat(memberships.members)
                    .isNotNull
                    .isNotEmpty
            }
            .verifyComplete()
    }

    @Test
    fun `create 2 users, one room, both join, 1 sends message and receive`() {
        Hooks.onOperatorDebug()

        val roomName = randomAlphaNumeric(4) + "Room"
        val roomCreateReq = ByStringRequest(roomName)

        val roomCreated = topicService.addRoom(roomCreateReq).block()!!
        val user1 = userService.addUser(UserCreateRequest("user", "testuser", "http://")).block()!!
        val user2 = userService.addUser(UserCreateRequest("user2", "testuser2", "http://")).block()!!


        topicService.joinRoom(MembershipRequest(user1.id, roomCreated.id)).block()
        topicService.joinRoom(MembershipRequest(user2.id, roomCreated.id)).block()

        val message = MessageSendRequest("Hello World", user1.id, roomCreated.id)

        messagingService.send(message).block()

        StepVerifier
            .create(
                messagingService
                    .listenTopic(ByIdRequest(roomCreated.id))
                    .take(1)
            )
            .assertNext { msg ->
                Assertions
                    .assertThat(msg)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("data", msg.data)
            }
            .verifyComplete()
    }

}