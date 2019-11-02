package com.demo.chat.test.persistence

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatMembershipRepository
import com.demo.chat.service.ChatMembershipPersistence
import com.demo.chat.service.KeyService
import com.demo.chat.service.persistence.ChatMembershipPersistenceCassandra
import com.demo.chat.test.TestKeyService
import com.demo.chat.test.anyObject
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class MembershipPersistenceTests {
    lateinit var svc: ChatMembershipPersistence

    @MockBean
    lateinit var repo: ChatMembershipRepository

    private val keyService: KeyService = TestKeyService

    private val keyId = UUIDs.timeBased()
    private val memberId = UUIDs.timeBased()
    private val topicId = UUIDs.timeBased()

    private val testChatMembership = ChatMembership(
            MemberShipKey(keyId),
            CSEventKeyType(memberId),
            CSEventKeyType(topicId)
    )

    @BeforeEach
    fun setUp() {
        BDDMockito
                .given(repo.findByKeyId(anyObject()))
                .willReturn(Mono.just(testChatMembership))

        BDDMockito
                .given(repo.findAll())
                .willReturn(Flux.just(testChatMembership))

        BDDMockito
                .given(repo.findByKeyIdIn(anyObject()))
                .willReturn(Flux.just(testChatMembership))

        BDDMockito
                .given(repo.save(Mockito.any(ChatMembership::class.java)))
                .willReturn(Mono.empty())

        BDDMockito
                .given(repo.deleteById(Mockito.any(UUID::class.java)))
                .willReturn(Mono.empty())

        svc = ChatMembershipPersistenceCassandra(keyService, repo)
    }

    @Test
    fun `gets all memberships`() {
        val allMemberships = svc.all()

        StepVerifier
                .create(allMemberships)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }

    @Test
    fun `add the membership, finds all` () {
        val saveNFind = svc
                .add(testChatMembership)
                .thenMany(svc.all())

        StepVerifier
                .create(saveNFind)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }

    @Test
    fun `deletes a membership`() {
        StepVerifier
                .create(svc.rem(testChatMembership.key))
                .verifyComplete()
    }

    @Test
    fun `gets a single membership`() {
        StepVerifier
                .create(svc.get(testChatMembership.key))
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }
}