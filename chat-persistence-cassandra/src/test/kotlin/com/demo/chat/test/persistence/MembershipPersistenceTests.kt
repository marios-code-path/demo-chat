package com.demo.chat.test.persistence

import com.demo.chat.domain.Key
import com.demo.chat.persistence.cassandra.domain.TopicMembershipByKey
import com.demo.chat.persistence.cassandra.repository.TopicMembershipRepository
import com.demo.chat.service.MembershipPersistence
import com.demo.chat.persistence.cassandra.impl.MembershipPersistenceCassandra
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestUUIDKeyService
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
    lateinit var membershipPersistence: MembershipPersistence<UUID>

    @MockBean
    lateinit var repo: TopicMembershipRepository<UUID>

    private val keyService = TestUUIDKeyService()

    private val testChatMembership = TopicMembershipByKey(keyService.nextKey(), keyService.nextKey(), keyService.nextKey())

    @BeforeEach
    fun setUp() {
        BDDMockito
                .given(repo.findByKey(TestBase.anyObject()))
                .willReturn(Mono.just(testChatMembership))

        BDDMockito
                .given(repo.save(TestBase.anyObject()))
                .willReturn(Mono.empty<TopicMembershipByKey<UUID>>())

        BDDMockito
                .given(repo.findAll())
                .willReturn(Flux.just(testChatMembership))

        BDDMockito
                .given(repo.findByKeyIn(TestBase.anyObject()))
                .willReturn(Flux.just(testChatMembership))

        BDDMockito
                .given(repo.deleteById(Mockito.any(UUID::class.java)))
                .willReturn(Mono.empty())

        membershipPersistence = MembershipPersistenceCassandra(keyService, repo)
    }

    @Test
    fun `gets all memberships`() {
        val allMemberships = membershipPersistence.all()

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
    fun `add the membership, finds all`() {
        val saveNFind = membershipPersistence
                .add(testChatMembership)
                .thenMany(membershipPersistence.all())

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
                .create(membershipPersistence.rem(Key.funKey(testChatMembership.key)))
                .verifyComplete()
    }

    @Test
    fun `gets a single membership`() {
        StepVerifier
                .create(membershipPersistence.get(Key.funKey(testChatMembership.key)))
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }
}