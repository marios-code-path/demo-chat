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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatServiceCassandra::class])
@OverrideAutoConfiguration(enabled = true)
@ImportAutoConfiguration(classes = [ChatServiceCassandra::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class ChatServiceTests {

    val logger = LoggerFactory.getLogger("TESTCASE ")
    @Autowired
    lateinit var service: ChatServiceCassandra

    @MockBean
    lateinit var msgRepo: ChatMessageRepository

    @MockBean
    lateinit var msgRoomRepo: ChatMessageRoomRepository

    @MockBean
    lateinit var msgUserRepo: ChatMessageUserRepository


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


        Mockito.`when`(userRepo.findByKeyUserId(anyObject<UUID>()))
                .thenReturn(Mono.just(newUser))

        Mockito.`when`(userRepo.insert(anyObject<ChatUser>()))
                .thenReturn(Mono.just(newUser))

        Mockito.`when`(roomRepo.insert(anyObject<ChatRoom>()))
                .thenReturn(Mono.just(newRoom))

        Mockito.`when`(roomRepo.joinRoom(anyObject<UUID>(), anyObject<UUID>()))
                .thenReturn(Mono.just(true))

        Mockito.`when`(roomRepo.findByKeyRoomId(anyObject<UUID>()))
                .thenReturn(Mono.just(newRoom))

        Mockito.`when`(roomRepo.leaveRoom(anyObject<UUID>(), anyObject<UUID>()))
                .thenReturn(Mono.just(true))

        Mockito.`when`(msgRepo.saveMessage(anyObject<ChatMessage>()))
                .thenReturn(Mono.just(newMessage))

        Mockito.`when`(msgRoomRepo.findByKeyRoomId(anyObject<UUID>()))
                .thenReturn(Flux.just(byRoomMessage))
    }

    @Test
    fun `should send to room and receive messages from room`() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val messages = service
                .sendMessage(userId, roomId, "")
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
                                        { assertEquals("SUP TEST", it.first().text) })
                            }

                    )
                }
                .verifyComplete()
    }

    @Test
    fun `should send message to room`() {
        val sendMessageFlux = service
                .sendMessage(UUIDs.timeBased(), UUID.randomUUID(), "Hello there")

        StepVerifier
                .create(sendMessageFlux)
                .expectSubscription()
                .assertNext {
                    assertAll("message",
                            { assertNotNull(it.key.userId) },
                            { assertNotNull(it.key.roomId) },
                            { assertEquals(it.text, "SUP TEST") }
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
                .assertNext(Assertions::assertTrue)
                .verifyComplete()
    }
}