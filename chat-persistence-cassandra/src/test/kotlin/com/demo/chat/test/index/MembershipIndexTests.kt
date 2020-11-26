package com.demo.chat.test.index

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.cassandra.TopicMembershipByMember
import com.demo.chat.domain.cassandra.TopicMembershipByMemberOf
import com.demo.chat.repository.cassandra.TopicMembershipByMemberOfRepository
import com.demo.chat.repository.cassandra.TopicMembershipByMemberRepository
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.index.MembershipIndexCassandra
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
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class MembershipIndexTests {

    private lateinit var membershipIndex: MembershipIndexService<UUID, Map<String, String>>

    @MockBean
    private lateinit var byMemberOfRepository: TopicMembershipByMemberOfRepository<UUID>

    @MockBean
    private lateinit var byMemberRepository: TopicMembershipByMemberRepository<UUID>

    @BeforeEach
    fun setUp() {

        val membership = TopicMembershipByMember(UUIDs.timeBased(), UUIDs.timeBased(), UUIDs.timeBased())
        val membershipOf = TopicMembershipByMemberOf(UUIDs.timeBased(), UUIDs.timeBased(), UUIDs.timeBased())

        BDDMockito
                .given(byMemberOfRepository.save(anyObject<TopicMembershipByMemberOf<UUID>>()))
                .willReturn(Mono.just(membershipOf))

        BDDMockito
                .given(byMemberRepository.save(anyObject<TopicMembershipByMember<UUID>>()))
                .willReturn(Mono.just(membership))

        BDDMockito
                .given(byMemberOfRepository.delete(anyObject<TopicMembershipByMemberOf<UUID>>()))
                .willReturn(Mono.empty())

        BDDMockito.given(byMemberRepository.delete(anyObject<TopicMembershipByMember<UUID>>()))
                .willReturn(Mono.empty())

        BDDMockito.given(byMemberRepository.findByMember(anyObject()))
                .willReturn(Flux.just(membership))

        BDDMockito.given(byMemberOfRepository.findByMemberOf(anyObject()))
                .willReturn(Flux.just(membershipOf))

        this.membershipIndex = MembershipIndexCassandra(UUID::fromString, byMemberRepository, byMemberOfRepository)
    }

    @Test
    fun `should membershipIndex save one`() {
        StepVerifier
                .create(membershipIndex.add(TopicMembership.create(UUIDs.timeBased(), UUIDs.timeBased(), UUIDs.timeBased())))
                .verifyComplete()
    }

    @Test
    fun `should fetch by member`() {
        StepVerifier
                .create(
                        membershipIndex.findBy(mapOf(Pair(MembershipIndexService.MEMBER, UUID.randomUUID().toString())))
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .extracting("id")
                            .isInstanceOf(UUID::class.java)
                }
                .verifyComplete()
    }

    @Test
    fun `should fetch by memberOf`() {
        StepVerifier
                .create(
                        membershipIndex.findBy(mapOf(Pair(MembershipIndexService.MEMBEROF, UUID.randomUUID().toString())))
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .extracting("id")
                            .isInstanceOf(UUID::class.java)
                }
                .verifyComplete()
    }
}