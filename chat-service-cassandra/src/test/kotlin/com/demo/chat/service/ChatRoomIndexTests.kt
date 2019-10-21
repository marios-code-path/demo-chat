package com.demo.chat.service

import com.demo.chat.domain.EventKey
import com.demo.chat.domain.Room
import com.demo.chat.domain.RoomKey
import com.demo.chat.service.persistence.ChatRoomPersistenceCassandra
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class ChatRoomIndexTests {

    lateinit var roomSvc: ChatRoomPersistenceCassandra

    lateinit var roomIndex: ChatRoomIndexService

    private val rid: EventKey = EventKey.create(UUID.randomUUID())

    private val uid: EventKey = EventKey.create(UUID.randomUUID())

    @Test
    fun `should join and leave a ficticious room`() {
        val serviceFlux = roomIndex
                .addMember(uid, rid)
                .thenMany(roomIndex.remMember(uid, rid))

        StepVerifier
                .create(serviceFlux)
                .expectSubscription()
                .expectNextCount(0)
                .verifyComplete()
    }

    @Test
    fun `should create room fetch by name`() {
        StepVerifier
                .create(
                        Flux.just(randomAlphaNumeric(5), randomAlphaNumeric(5))
                                .flatMap { name ->
                                    roomSvc.key()
                                            .flatMap {key ->
                                                roomSvc.add(
                                                        Room.create(
                                                                RoomKey.create( key.id, name),
                                                                setOf()
                                                        )
                                                )
                                            }
                                }
                                .thenMany(
                                        roomIndex.findBy(mapOf(Pair("name", "test-room")))
                                )
                )
                .expectSubscription()
                .assertNext(this::roomKeyAssertions)
                .verifyComplete()
    }

    private fun roomKeyAssertions(key: RoomKey) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(key) },
                { Assertions.assertNotNull(key.id) },
                { Assertions.assertNotNull(key.name) }
        )
    }
}