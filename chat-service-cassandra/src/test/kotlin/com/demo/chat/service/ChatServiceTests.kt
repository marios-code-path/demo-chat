package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.repository.*
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatServiceCassandra::class])
@OverrideAutoConfiguration(enabled = true)
@ImportAutoConfiguration(classes = [ChatServiceCassandra::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class ChatServiceTests {

    val logger = LoggerFactory.getLogger("TESTCASE ")

    lateinit var service: ChatServiceCassandra

    @MockBean
    lateinit var msgRepo: ChatMessageRepository

    @MockBean
    lateinit var msgUserRepo: ChatMessageUserRepository

    @MockBean
    lateinit var msgRoomRepo: ChatMessageRoomRepository

    @MockBean
    lateinit var roomRepo: ChatRoomRepository

    @MockBean
    lateinit var userRepo: ChatUserRepository

    val rid: UUID = UUID.randomUUID()

    val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newUser = ChatUser(ChatUserKey(uid, "test-handle"), "test-name", Instant.now())
        val newRoom = ChatRoom(ChatRoomKey(rid, "test-room"), emptySet(), Instant.now())
        val newMessage = ChatMessage(ChatMessageKey(UUID.randomUUID(), uid, rid, Instant.now()), "SUP TEST", true)
        val byRoomMessage = ChatMessageRoom(ChatMessageRoomKey(UUID.randomUUID(), uid, rid, Instant.now()), "SUP TEST", true)


        Mockito.`when`(userRepo.findByKeyUserId(anyObject()))
                .thenReturn(Mono.just(newUser))

        Mockito.`when`(userRepo.insert(anyObject<ChatUser>()))
                .thenReturn(Mono.just(newUser))

        Mockito.`when`(roomRepo.insert(anyObject<ChatRoom>()))
                .thenReturn(Mono.just(newRoom))

        Mockito.`when`(roomRepo.joinRoom(anyObject(), anyObject()))
                .thenReturn(Mono.empty())

        Mockito.`when`(roomRepo.findByKeyRoomId(anyObject()))
                .thenReturn(Mono.just(newRoom))

        Mockito.`when`(roomRepo.leaveRoom(anyObject(), anyObject()))
                .thenReturn(Mono.empty())

        Mockito.`when`(msgRepo.saveMessage(anyObject()))
                .thenReturn(Mono.just(newMessage))

        Mockito.`when`(msgRoomRepo.findByKeyRoomId(anyObject()))
                .thenReturn(Flux.just(byRoomMessage))

        service = ChatServiceCassandra(
                userRepo,
                roomRepo,
                msgRepo,
                msgRoomRepo,
                msgUserRepo
        )
    }

    @Test
    fun `dud test`() {
        val messages = service.getMessagesForRoom(UUID.randomUUID(), UUID.randomUUID())
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

        val messages = service
                .storeMessage(userId, roomId, "")
                .flatMap {
                    service.getMessagesForRoom(userId, roomId)
                            .collectList()
                }

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

    @Test
    fun `should send message to room`() {
        val sendMessageFlux = service
                .storeMessage(UUIDs.timeBased(), UUID.randomUUID(), "Hello there")

        StepVerifier
                .create(sendMessageFlux)
                .expectSubscription()
                .assertNext {
                    assertAll("message",
                            { assertNotNull(it.key.userId) },
                            { assertNotNull(it.key.roomId) },
                            { assertEquals(it.value, "SUP TEST") }
                    )
                }
                .verifyComplete()
    }

    @Test
    fun `should join and leave a ficticious room`() {
        val serviceFlux = service
                .joinRoom(uid, rid)
                .thenMany(service.leaveRoom(uid, rid))

        StepVerifier
                .create(serviceFlux)
                .expectSubscription()
                .expectNextCount(0)
                .verifyComplete()
    }
}