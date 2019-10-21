package com.demo.chat.service

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.service.persistence.TextMessagePersistenceCassandra
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class ChatMessageByIdServiceTests {

    val logger = LoggerFactory.getLogger(this::class.simpleName)

    lateinit var msgSvc: TextMessagePersistenceCassandra

    @MockBean
    lateinit var msgRepo: ChatMessageRepository

    @MockBean
    lateinit var msgByTopicRepo: ChatMessageByTopicRepository

    private val keyService: KeyService = TestKeyService

    private val rid: UUID = UUID.randomUUID()

    private val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newMessage = ChatMessageById(ChatMessageByIdKey(UUID.randomUUID(), rid, uid, Instant.now()), "SUP TEST", true)
        val byRoomMessage = ChatMessageByTopic(ChatMessageByTopicKey(UUID.randomUUID(), rid, uid, Instant.now()), "SUP TEST", true)

        Mockito.`when`(msgRepo.add(anyObject()))
                .thenReturn(Mono.empty())

        Mockito.`when`(msgByTopicRepo.findByKeyTopicId(anyObject()))
                .thenReturn(Flux.just(byRoomMessage))

        msgSvc = TextMessagePersistenceCassandra(keyService, msgRepo, msgByTopicRepo)
    }

    @Test
    fun `dud test`() {
        val messages = msgSvc.getAll(UUID.randomUUID())
                .doOnNext {
                    logger.info("A message found; ${it}")
                }

        StepVerifier
                .create(messages)
                .assertNext {
                    org.assertj.core.api.Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                }
                .verifyComplete()
    }

    @Test
    fun `should send to room and receive messages from room`() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val messages = msgSvc.key(userId, roomId)
                .flatMap {
                    msgSvc.add(it, "")
                }
                .thenMany(msgSvc.getAll(roomId).collectList())

        StepVerifier
                .create(messages)
                .expectSubscription()
                .assertNext {
                    assertAll("messages",
                            { assertNotNull(it) },
                            {
                                MatcherAssert
                                        .assertThat(it, Matchers
                                                .not((Matchers.emptyCollectionOf(Message::class.java)))
                                        )
                            },
                            {
                                assertAll("message",
                                        { assertEquals("SUP TEST", it.first().value) })
                            }

                    )
                }
                .verifyComplete()
    }
}