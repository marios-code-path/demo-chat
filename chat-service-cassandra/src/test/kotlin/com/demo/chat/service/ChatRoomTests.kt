package com.demo.chat.service

import com.demo.chat.domain.ChatRoom
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*
import kotlin.collections.HashSet

@ExtendWith(SpringExtension::class)
class ChatRoomTests {

    @Test
    fun `should hold members in room`() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val memberSet = HashSet<UUID>()
        memberSet.add(userId)

        val room = ChatRoom(roomId, "Conversation", memberSet, Instant.now())

        StepVerifier
                .create(Flux.just(room))
                .assertNext {
                    roomAssertions(it)
                    assertAll("member set is populated",
                            { Assertions.assertNotNull(it.members) },
                            { Assertions.assertTrue(it.members!!.isNotEmpty()) })
                }
                .verifyComplete()
    }

    fun roomAssertions(room: ChatRoom) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.id) },
                { Assertions.assertNotNull(room.name) },
                { Assertions.assertNotNull(room.timestamp) }
        )
    }

}