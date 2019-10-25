package com.demo.chat.test.persistence

import com.demo.chat.domain.ChatUser
import com.demo.chat.domain.ChatUserKey
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.service.KeyService
import com.demo.chat.service.persistence.ChatUserPersistenceCassandra
import com.demo.chat.test.TestKeyService
import com.demo.chat.test.anyObject
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
class UserPersistenceTests {

    lateinit var userSvc: ChatUserPersistenceCassandra

    @MockBean
    lateinit var userRepo: ChatUserRepository

    private val keyService: KeyService = TestKeyService

    val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newUser = ChatUser(ChatUserKey(uid, "test-handle"), "test-name", "", Instant.now())

        Mockito.`when`(userRepo.findByKeyId(anyObject()))
                .thenReturn(Mono.just(newUser))

        Mockito.`when`(userRepo.insert(anyObject<ChatUser>()))
                .thenReturn(Mono.just(newUser))

        userSvc = ChatUserPersistenceCassandra(keyService, userRepo)
    }

}