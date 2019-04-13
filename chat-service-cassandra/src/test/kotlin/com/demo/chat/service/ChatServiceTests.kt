package com.demo.chat.service

import com.demo.chat.domain.ChatMessage
import com.demo.chat.domain.ChatMessageKey
import com.demo.chat.domain.ChatRoom
import com.demo.chat.domain.ChatUser
import com.demo.chat.repository.ChatMessageRepository
import com.demo.chat.repository.ChatRoomRepository
import com.demo.chat.repository.ChatUserRepository
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatService::class])
@OverrideAutoConfiguration(enabled = true)
@ImportAutoConfiguration(classes = [ChatService::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class ChatServiceTests {

    @Autowired
    lateinit var service: ChatService

    @MockBean
    lateinit var msgRepo: ChatMessageRepository

    @MockBean
    lateinit var roomRepo: ChatRoomRepository

    @MockBean
    lateinit var userRepo: ChatUserRepository

    val rid: UUID = UUID.randomUUID()

    val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newUser = ChatUser(uid, "test-handle", "test-name", Date())
        val newRoom = ChatRoom(rid, "test-room", emptySet(), Date())
        val newMessage = ChatMessage(ChatMessageKey(UUID.randomUUID(), uid, rid, Instant.now()), "SUP TEST", true)

        Mockito.`when`(userRepo.findById(anyObject<UUID>()))
                .thenReturn(Mono.just(newUser))

        Mockito.`when`(userRepo.insert(anyObject<ChatUser>()))
                .thenReturn(Mono.just(newUser))

        Mockito.`when`(roomRepo.insert(anyObject<ChatRoom>()))
                .thenReturn(Mono.just(newRoom))

        Mockito.`when`(roomRepo.joinRoom(anyObject<UUID>(), anyObject<UUID>()))
                .thenReturn(Mono.just(true))

        Mockito.`when`(roomRepo.findById(anyObject<UUID>()))
                .thenReturn(Mono.just(newRoom))

        Mockito.`when`(roomRepo.leaveRoom(anyObject<UUID>(), anyObject<UUID>()))
                .thenReturn(Mono.just(true))

        Mockito.`when`(msgRepo.insert(anyObject<ChatMessage>()))
                .thenReturn(Mono.just(newMessage))

        Mockito.`when`(msgRepo.findByKeyRoomId(anyObject<UUID>()))
                .thenReturn(Flux.just(newMessage))
    }

    @Test
    fun `should send and receive messages from room`() {
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
                                                .not((Matchers.emptyCollectionOf(ChatMessage::class.java)))
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
                .sendMessage(UUID.randomUUID(), UUID.randomUUID(), "Hello there")

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