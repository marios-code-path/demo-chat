package com.demo.chat.test.index

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatRoomNameRepository
import com.demo.chat.repository.cassandra.ChatRoomRepository
import com.demo.chat.service.RoomIndexService
import com.demo.chat.service.index.RoomIndexCassandra
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

private val ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

fun randomAlphaNumeric(size: Int): String {
    var count = size
    val builder = StringBuilder()
    while (count-- != 0) {
        val character = (Math.random() * ALPHA_NUMERIC_STRING.length).toInt()
        builder.append(ALPHA_NUMERIC_STRING[character])
    }
    return builder.toString()
}

fun <T> anyObject(): T {
    Mockito.anyObject<T>()
    return uninitialized()
}

fun <T> uninitialized(): T = null as T

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class RoomIndexTests {

    @MockBean
    lateinit var roomRepo: ChatRoomRepository

    @MockBean
    lateinit var nameRepo: ChatRoomNameRepository

    lateinit var roomIndex: RoomIndexService

    @BeforeEach
    fun setUp() {
        val idRoom = ChatRoom(ChatRoomKey(rid.id, ROOMNAME), setOf(), true, Instant.now())
        val nameRoom = ChatRoomName(ChatRoomNameKey(rid.id, ROOMNAME), setOf(), true, Instant.now())

        BDDMockito
                .given(roomRepo.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(nameRepo.save(Mockito.any(ChatRoomName::class.java)))
                .willReturn(Mono.just(nameRoom))

        BDDMockito
                .given(roomRepo.rem(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(nameRepo.findByKeyName(anyObject()))
                .willReturn(Mono.just(nameRoom))

        BDDMockito.given(roomRepo.findByKeyId(anyObject()))
                .willReturn(Mono.just(idRoom))

        BDDMockito.given(roomRepo.join(anyObject(), anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomRepo.leave(anyObject(), anyObject()))
                .willReturn(Mono.empty ())

        roomIndex = RoomIndexCassandra(roomRepo, nameRepo)
    }

    private val rid: EventKey = EventKey.create(UUID.randomUUID())

    private val uid: EventKey = EventKey.create(UUID.randomUUID())

    private val ROOMNAME = "ROOM_TEST"

    @Test
    fun `should create 2 rooms, fetch by random name`() {
        StepVerifier
                .create(
                        roomIndex.findBy(mapOf(Pair("name", randomAlphaNumeric(5))))
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