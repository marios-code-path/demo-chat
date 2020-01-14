package com.demo.chat.test.repository

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.domain.cassandra.*
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
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*
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
    lateinit var repo: ChatMembershipRepository<UUID>

    @Autowired
    lateinit var byMemberRepo: ChatMembershipByMemberRepository<UUID>

    @Autowired
    lateinit var byMemberOfRepo: ChatMembershipByMemberOfRepository<UUID>

    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    @Test
    fun `membershipOf should not return all`() {
        val membership = ChatMembership(
                ChatMembershipKey(UUIDs.timeBased()),
                CassandraUUIDKeyType(UUIDs.timeBased()),
                CassandraUUIDKeyType(UUIDs.timeBased())
        )

        val membershipSave = repo
                .save(membership)
                .thenMany(byMemberOfRepo.findAll())

        StepVerifier
                .create(membershipSave)
                .expectSubscription()
                .verifyComplete()
    }

    @Test
    fun `should save, find all`() {
        val membership = ChatMembership(
                ChatMembershipKey(UUIDs.timeBased()),
                CassandraUUIDKeyType(UUIDs.timeBased()),
                CassandraUUIDKeyType(UUIDs.timeBased())
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
        val keyId = ChatMembershipKey(UUIDs.timeBased())
        val membership = ChatMembership(
                keyId,
                CassandraUUIDKeyType(UUIDs.timeBased()),
                CassandraUUIDKeyType(UUIDs.timeBased())
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
    fun `should save, delete one, find remaining one by all`() {
        val memberships = mutableSetOf<ChatMembership<UUID>>()
        val streamOfMemberships = Flux.generate <ChatMembershipKey<UUID>> { s -> s.next(ChatMembershipKey(UUIDs.timeBased())) }
                .take(2)
                .map {key ->
                    val i = ChatMembership(
                            key,
                            CassandraUUIDKeyType(UUIDs.timeBased()),
                            CassandraUUIDKeyType(UUIDs.timeBased())
                    )
                    memberships.add(i)

                    i
                }

        val membershipSave = repo
                .saveAll(streamOfMemberships)
                .collectList()
                .flatMap {
                    repo.delete(memberships.last())
                }
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

    //@Test
    fun `should save, find by memberId`() {
        val keyId = ChatMembershipKeyByMember(UUIDs.timeBased())
        val membership = ChatMembershipByMember(
                CassandraUUIDKeyType(UUIDs.timeBased()),
                keyId,
                CassandraUUIDKeyType(UUIDs.timeBased())
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

    //@Test
    fun `should save, find by memberOf`() {
        val keyId = ChatMembershipKeyByMemberOf(UUIDs.timeBased())
        val membership = ChatMembershipByMemberOf(
                CassandraUUIDKeyType(UUIDs.timeBased()),
                CassandraUUIDKeyType(UUIDs.timeBased()),
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
            Key.anyKey(UUIDs.timeBased())
        }.limit(5).toList()

        val keyIdsList = keyList.stream().map { it.id }.toList()
        val memberships = Flux.fromIterable(keyList
                .map {
                    ChatMembership(ChatMembershipKey(it.id),
                            CassandraUUIDKeyType(UUIDs.timeBased()),
                            CassandraUUIDKeyType((UUIDs.timeBased())))
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