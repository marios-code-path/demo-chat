package com.demo.chat.chatconsumers

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [ChatConsumersApplication::class])
class RoomConsumerTests {
    val logger = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    private lateinit var roomClient: ChatRoomClient

    private var testName: String = "Ferris The Dog"
    private var testHandle: String = "meatman"
    private var testImageUri: String = "http://pivotal.io/big.gif"

    private var roomName: String = "K9"

    @Test
    fun `should create a room`() {
        var pub = roomClient.callCreateRoom(roomName)

        StepVerifier
                .create(pub)
                .expectSubscription()
                .verifyComplete()
    }

    @Test
    fun `should fetch room by name`() {
        var pub = roomClient.callGetRoomByName(roomName)

        StepVerifier
                .create(pub)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.key)
                            .hasFieldOrPropertyWithValue("name", roomName)
                }
                .verifyComplete()
    }

    @Test
    fun `should fetch room by id`() {
        var pub = roomClient
                .callGetRooms()
                .flatMap {
                    roomClient.callGetRoom(it.key.id)
                }

        StepVerifier
                .create(pub)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it)
                            .hasFieldOrPropertyWithValue("name", roomName)
                }
    }
}