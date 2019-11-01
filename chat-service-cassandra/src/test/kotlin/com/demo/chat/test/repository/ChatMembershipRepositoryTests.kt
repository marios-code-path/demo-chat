package com.demo.chat.test.repository

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatMembershipByMemberOfRepository
import com.demo.chat.repository.cassandra.ChatMembershipByMemberRepository
import com.demo.chat.repository.cassandra.ChatMembershipRepository
import com.demo.chat.test.TestConfiguration
import org.assertj.core.api.Assertions
import org.cassandraunit.spring.CassandraDataSet
import org.cassandraunit.spring.CassandraUnit
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.stream.Stream
import kotlin.streams.toList

//https://stackoverflow.com/questions/38862460/user-defined-type-with-spring-data-cassandra/42036202#42036202

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [TestConfiguration::class])
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-membership.cql")
class ChatMembershipRepositoryTests {
    @Autowired
    lateinit var repo: ChatMembershipRepository

    @Autowired
    lateinit var byMemberRepo: ChatMembershipByMemberRepository

    @Autowired
    lateinit var byMemberOfRepo: ChatMembershipByMemberOfRepository

    @BeforeEach
    fun setUp() {
        repo.deleteAll().block()
    }
    
    @Test
    fun `should save, find all`() {
        val membership = ChatMembership(
                MemberShipKey(UUIDs.timeBased()),
                CSEventKeyType(UUIDs.timeBased()),
                CSEventKeyType(UUIDs.timeBased())
        )

        val membershipSave = repo
                .save(membership)
                .thenMany(repo.findAll())

        StepVerifier
                .create(membershipSave)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }

    @Test
    fun `should save, find by key id`() {
        val keyId = MemberShipKey(UUIDs.timeBased())
        val membership = ChatMembership(
                keyId,
                CSEventKeyType(UUIDs.timeBased()),
                CSEventKeyType(UUIDs.timeBased())
        )

        val membershipSave = repo
                .save(membership)
                .thenMany(repo.findByKeyId(keyId.id))

        StepVerifier
                .create(membershipSave)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }

    @Test
    fun `should save, find by memberId`() {
        val keyId = MemberShipKeyByMember(UUIDs.timeBased())
        val membership = ChatMembershipByMember(
                CSEventKeyType(UUIDs.timeBased()),
                keyId,
                CSEventKeyType(UUIDs.timeBased())
        )

        val membershipSave = byMemberRepo
                .save(membership)
                .thenMany(byMemberRepo.findByMemberId(keyId.id))

        StepVerifier
                .create(membershipSave)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }

    @Test
    fun `should save, find by memberOf`() {
        val keyId = MemberShipKeyByMemberOf(UUIDs.timeBased())
        val membership = ChatMembershipByMemberOf(
                CSEventKeyType(UUIDs.timeBased()),
                CSEventKeyType(UUIDs.timeBased()),
                keyId
        )

        val membershipSave = byMemberOfRepo
                .save(membership)
                .thenMany(byMemberOfRepo.findByMemberOfId(keyId.id))

        StepVerifier
                .create(membershipSave)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }

    @Test
    fun `should save many find by Ids`() {
        val keyList = Stream.generate {
            EventKey.create(UUIDs.timeBased())
        }.limit(5).toList()

        val keyIdsList = keyList.stream().map { it.id }.toList()
        val memberships = Flux.fromIterable(keyList
                .map {
                    ChatMembership(MemberShipKey(it.id),
                            CSEventKeyType(UUIDs.timeBased()),
                            CSEventKeyType((UUIDs.timeBased())))
                })

        val repoStream = repo
                .saveAll(memberships)
                .thenMany(repo.findByKeyIdIn(keyIdsList))

        StepVerifier
                .create(repoStream)
                .expectSubscription()
                .expectNextCount(5)
                .verifyComplete()
    }
}