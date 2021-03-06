package com.demo.chat.test.persistence

import com.demo.chat.domain.cassandra.ChatUser
import com.demo.chat.domain.cassandra.ChatUserKey
import com.demo.chat.domain.Key
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.UserPersistenceCassandra
import com.demo.chat.test.TestStringKeyService
import com.demo.chat.test.TestUUIDKeyService
import com.demo.chat.test.anyObject
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class UserPersistenceTests {

    lateinit var userSvc: UserPersistenceCassandra<UUID>

    @MockBean
    lateinit var userRepo: ChatUserRepository<UUID>

    private val keyService: IKeyService<UUID> = TestUUIDKeyService()

    val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newUser = ChatUser(ChatUserKey(uid), "test-name", "test-handle", "", Instant.now())

        BDDMockito
                .given(userRepo.findByKeyIdIn(anyObject()))
                .willReturn(Flux.just(newUser))

        BDDMockito
                .given(userRepo.findByKeyId(anyObject()))
                .willReturn(Mono.just(newUser))

        BDDMockito
                .given(userRepo.add(anyObject<ChatUser<UUID>>()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(userRepo.rem(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(userRepo.findAll())
                .willReturn(Flux.just(newUser))

        userSvc = UserPersistenceCassandra(keyService, userRepo)
    }

    @Test
    fun `should get key and remove associated non-existent object`() {
        val publisher = userSvc
                .key()
                .flatMap {
                    userSvc.rem(it)
                }

        StepVerifier
                .create(publisher)
                .expectSubscription()
                .verifyComplete()
    }

    @Test
    fun `should get many by Ids`() {
        val publisher = userSvc.byIds(listOf(Key.anyKey(UUID.randomUUID())))

        StepVerifier
                .create(publisher)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }

    @Test
    fun `should get single`() {
        val publisher = userSvc.get(Key.anyKey(UUID.randomUUID()))

        StepVerifier
                .create(publisher)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }

    @Test
    fun `should save and find users`() {
        val newUser = ChatUser(ChatUserKey(uid), "test-name", "test-handle", "", Instant.now())

        val publisher = userSvc
                .add(newUser)
                .thenMany(userSvc.all())

        StepVerifier
                .create(publisher)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }

}