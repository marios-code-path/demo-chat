package com.demo.chat.service

import com.demo.chat.domain.ChatUser
import com.demo.chat.domain.ChatUserHandle
import com.demo.chat.domain.ChatUserHandleKey
import com.demo.chat.domain.ChatUserKey
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class ChatUserPersistenceTests {

    lateinit var userSvc: ChatUserPersistenceCassandra

    @MockBean
    lateinit var userRepo: ChatUserRepository

    @MockBean
    lateinit var userHandleRepo: ChatUserHandleRepository

    private val keyService: KeyService = TestKeyService

    val rid: UUID = UUID.randomUUID()

    val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newUser = ChatUser(ChatUserKey(uid, "test-handle"), "test-name", "", Instant.now())
        val newUserHandle = ChatUserHandle(ChatUserHandleKey(uid, "test-handle"), "test-name", "", Instant.now())

        Mockito.`when`(userRepo.findByKeyId(anyObject()))
                .thenReturn(Mono.just(newUser))

        Mockito.`when`(userRepo.insert(anyObject<ChatUser>()))
                .thenReturn(Mono.just(newUser))

        Mockito.`when`(userHandleRepo.findByKeyHandle(anyObject()))
                .thenReturn(Mono.just(newUserHandle))

        userSvc = ChatUserPersistenceCassandra(keyService, userRepo, userHandleRepo)

    }

}