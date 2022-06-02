package com.demo.chat.test.persistence

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.cassandra.ChatMessageById
import com.demo.chat.domain.cassandra.ChatMessageByIdKey
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.MessagePersistenceCassandra
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestUUIDKeyService
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
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
class TextMessageServiceTests {

    val logger = LoggerFactory.getLogger(this::class.simpleName)

    private val MSGTEXT = "SUP TEST"

    lateinit var msgSvc: MessagePersistenceCassandra<UUID>

    @MockBean
    lateinit var msgRepo: ChatMessageRepository<UUID>

    private val keyService: IKeyService<UUID> = TestUUIDKeyService()

    private val rid: UUID = UUID.randomUUID()

    private val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newMessage = ChatMessageById(ChatMessageByIdKey(UUID.randomUUID(), rid, uid, Instant.now()), MSGTEXT, true)

        Mockito.`when`(msgRepo.add(TestBase.anyObject()))
                .thenReturn(Mono.empty())

        BDDMockito
                .given(msgRepo.findAll())
                .willReturn(Flux.just(newMessage))

        msgSvc = MessagePersistenceCassandra(keyService, msgRepo)
    }

    @Test
    fun `should find all messages`() {
        val messages = msgSvc.all()
                .doOnNext {
                    logger.info("A message found: ${it}")
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

        val messages = keyService.key(Message::class.java)
                .flatMap {
                    msgSvc.add(
                            Message.create(
                                    MessageKey.create(it.id, roomId, userId),
                                    MSGTEXT,
                                    true
                            )
                    )
                }
                .thenMany(msgSvc.all().collectList())

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
                                        { assertEquals(MSGTEXT, it.first().data) })
                            }

                    )
                }
                .verifyComplete()
    }
}