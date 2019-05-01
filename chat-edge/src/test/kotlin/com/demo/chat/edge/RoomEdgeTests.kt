package com.demo.chat.edge

import com.demo.chat.domain.RoomMember
import com.demo.chat.domain.RoomMemberships
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
class RoomEdgeTests {
    private lateinit var roomEdge: ChatRoomEdge

    @Test
    fun `verify should create a room`() {
        val newRoom = roomEdge.createRoom("BootifulChat")

        StepVerifier
                .create(newRoom)
                .assertNext { roomAssertion(it) }
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
    fun `verify delete room`() {
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
    fun `verify get room memberships`() {
        val roomMemberships = roomEdge.roomMembers(testRoomId())

        StepVerifier
                .create(roomMemberships)
                .assertNext {
                    roomMembershipAssertions(it)
                }
                .verifyComplete()
    }

    fun roomMembershipAssertions(m: RoomMemberships) {
        MatcherAssert
                .assertThat("membership has at least one member", m,
                        Matchers.allOf(
                                Matchers.notNullValue(),
                                Matchers.hasProperty("members",
                                        Matchers.allOf(
                                                Matchers.notNullValue(),
                                                Matchers.not(Matchers.empty<RoomMember>())
                                        ))
                        ))
    }
}