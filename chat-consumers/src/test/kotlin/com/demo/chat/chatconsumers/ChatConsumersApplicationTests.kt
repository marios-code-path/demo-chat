package com.demo.chat.chatconsumers

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [ChatConsumersApplication::class])
class ChatConsumersApplicationTests {

    val logger = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    private lateinit var userClient: ChatUserClient

    @Autowired
    private lateinit var roomClient: ChatRoomClient


    private var userName: String = "meatman"
    private var userId: UUID = UUID.fromString("115a7700-8093-11e9-beee-416bd38e6cd4")

    @Test
    fun `should get a user`() {
        StepVerifier
                .create(userClient.callGetUser(userName))
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull

                    Assertions
                            .assertThat(it.user.key)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", "meatman")
                            .hasFieldOrPropertyWithValue("userId", userId)
                }
                .verifyComplete()
    }

    @Test
    fun `should create user`() {
        val randomHandle = randomAlphaNumeric(4)
        val randomName = randomAlphaNumeric(6)
        StepVerifier
                .create(userClient.callCreateUser(randomName, randomHandle))
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull

                    Assertions
                            .assertThat(it.user)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions.assertThat(it.user.key)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                }
                .verifyComplete()
    }


    @Test
    fun `should create User room join get room listing`() {
        val createUser = userClient
                .callCreateUser("Test Man", "meatman")

        val createRoom = roomClient
                .callCreateRoom("Perpperoni")

        val joinRoom = Flux.zip(createUser, createRoom)
                .flatMap { res ->
                    roomClient.callJoinRoom(res.t1.user.key.userId, res.t2.romKey.roomId)
                }
                .thenMany(roomClient.callGetRooms())
                .doOnNext {
                   logger.info("${it.room.key.roomId} is ${it.room.key.name}")
                    it.room.members?.forEach { uid ->
                        logger.info("member: $uid")
                    }
                }

        joinRoom.blockLast(Duration.ofMillis(5000))

    }

}
