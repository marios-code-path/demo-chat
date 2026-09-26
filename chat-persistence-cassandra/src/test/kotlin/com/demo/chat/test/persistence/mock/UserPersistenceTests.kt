package com.demo.chat.test.persistence.mock

import com.demo.chat.domain.User

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.test.key.FakeKeyServices

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.Key
import com.demo.chat.persistence.cassandra.domain.ChatUser
import com.demo.chat.persistence.cassandra.domain.ChatUserKey
import com.demo.chat.persistence.cassandra.repository.ChatUserRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.persistence.cassandra.impl.UserPersistenceCassandra
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestUUIDKeyService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.test.context.bean.override.mockito.MockitoBean
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

    @MockitoBean
    lateinit var userRepo: ChatUserRepository<UUID>

    private val keyService: IKeyService<UUID> = TestUUIDKeyService()

    private val roots = FakeKeyServices.uuidRoots()

    val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newUser = ChatUser(ChatUserKey(uid), "test-name", "test-handle", "", Instant.now())

        BDDMockito
                .given(userRepo.findByKeyIdIn(TestBase.anyObject()))
                .willReturn(Flux.just(newUser))

        BDDMockito
                .given(userRepo.findByKeyId(TestBase.anyObject()))
                .willReturn(Mono.just(newUser))

        BDDMockito
                .given(userRepo.add(TestBase.anyObject()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(userRepo.rem(TestBase.anyObject()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(userRepo.findAll())
                .willReturn(Flux.just(newUser))

        userSvc = UserPersistenceCassandra(keyService, roots, userRepo)
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
        val publisher = userSvc.byIds(listOf(TestKeys.key(UUID.randomUUID())))

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
        val publisher = userSvc.get(TestKeys.key(UUID.randomUUID()))

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
        val newUser = User.create(Key.of(uid, roots.of(ChatDomain.USER).id), "test-name", "test-handle", "")

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