package com.demo.chat.edge

import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
class ChatEdgeFunctionalityTests {

    private lateinit var userEdge: ChatUserEdge

    private lateinit var roomEdge: ChatRoomEdge

    private lateinit var topicEdge: ChatTopicEdge

    @Test
    fun `verify should create user`() {
        val userCreate = userEdge
                .createUser("Mario Gray", "darkbit1001")

        StepVerifier
                .create(userCreate)
                .assertNext { userAssertion(it) }
                .verifyComplete()
    }

    @Test
    fun `verify should get a user`() {
        val getUser = userEdge
                .getUser("darkbit1001")

        StepVerifier
                .create(getUser)
                .assertNext { userAssertion(it) }
                .verifyComplete()
    }

    @Test
    fun `verify should create a room`() {
        val newRoom = roomEdge.createRoom("BootifulChat")

        StepVerifier
                .create(newRoom)
                .assertNext{ roomAssertion(it) }
                .verifyComplete()
    }

    @Test
    fun `verify should get room info`() {
        val roomInfo = roomEdge.roomInfo(testRoomId())

        StepVerifier
                .create(roomInfo)
                .assertNext {
                    MatcherAssert
                            .assertThat("Room Info has State", it,
                                    Matchers.allOf(
                                            Matchers.notNullValue(),
                                            Matchers.hasProperty("totalMessages"),
                                            Matchers.hasProperty("totalMembers")
                                    ))
                }
    }

    @Test
    fun `verify delete room works by removing any details`() {
        val newRoom = roomEdge.createRoom("Valhalla")
        val delRoom = roomEdge.deleteRoom(testRoomId())

        StepVerifier
                .create(newRoom)
                .then {
                    delRoom.block()
                }
                .then {
                    roomEdge.roomInfo(testRoomId()).block()
                }
                .expectError()
                .verify()
    }

    @Test
    fun `verify should subscribe to topic`() {

    }

    @Test
    fun `verify should unsubscribe from topic`() {

    }

    @Test
    fun `verify should get topic member listing`() {

    }

    @Test
    fun `verify should send join alert on subscribe`() {

    }

    @Test
    fun `verify should receive topic feed`() {

    }

}
