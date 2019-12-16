package com.demo.chat.test.index

import com.demo.chat.domain.Key
import com.demo.chat.domain.UUIDKey
import com.demo.chat.domain.cassandra.ChatMessageTopic
import com.demo.chat.domain.cassandra.ChatMessageTopicName
import com.demo.chat.domain.cassandra.ChatRoomNameKey
import com.demo.chat.domain.cassandra.ChatTopicKey
import com.demo.chat.repository.cassandra.TopicByNameRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.index.TopicIndexCassandra
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
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
class MessageTopicIndexTests {

    @MockBean
    lateinit var roomRepo: TopicRepository

    @MockBean
    lateinit var nameRepo: TopicByNameRepository

    lateinit var topicIndex: TopicIndexService

    @BeforeEach
    fun setUp() {
        val idRoom = ChatMessageTopic(ChatTopicKey(rid.id), ROOMNAME, true)
        val nameRoom = ChatMessageTopicName(ChatRoomNameKey(rid.id, ROOMNAME), true)

        BDDMockito
                .given(roomRepo.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(nameRepo.save(Mockito.any(ChatMessageTopicName::class.java)))
                .willReturn(Mono.just(nameRoom))

        BDDMockito
                .given(roomRepo.rem(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(nameRepo.findByKeyName(anyObject()))
                .willReturn(Mono.just(nameRoom))

        BDDMockito.given(roomRepo.findByKeyId(anyObject()))
                .willReturn(Mono.just(idRoom))

        topicIndex = TopicIndexCassandra(roomRepo, nameRepo)
    }

    private val rid: UUIDKey = Key.eventKey(UUID.randomUUID())

    private val uid: UUIDKey = Key.eventKey(UUID.randomUUID())

    private val ROOMNAME = "ROOM_TEST"

    @Test
    fun `should create 2 rooms, fetch by random name`() {
        StepVerifier
                .create(
                        topicIndex.findBy(mapOf(Pair("name", randomAlphaNumeric(5))))
                )
                .expectSubscription()
                .assertNext(this::roomKeyAssertions)
                .verifyComplete()
    }

    private fun roomKeyAssertions(key: Key<UUID>) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(key) },
                { Assertions.assertNotNull(key.id) }
        )
    }
}