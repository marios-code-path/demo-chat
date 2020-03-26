package com.demo.chat.test.repository

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.Key
import com.demo.chat.domain.cassandra.TopicMembershipByKey
import com.demo.chat.domain.cassandra.TopicMembershipByMember
import com.demo.chat.domain.cassandra.TopicMembershipByMemberOf
import com.demo.chat.repository.cassandra.TopicMembershipByMemberOfRepository
import com.demo.chat.repository.cassandra.TopicMembershipByMemberRepository
import com.demo.chat.repository.cassandra.TopicMembershipRepository
import com.demo.chat.test.TestClusterConfiguration
import com.demo.chat.test.TestConfiguration
import org.assertj.core.api.Assertions
import org.cassandraunit.spring.CassandraDataSet
import org.cassandraunit.spring.CassandraUnit
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [TestConfiguration::class, TestClusterConfiguration::class])
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-membership.cql")
class TopicMembershipRepositoryTests {
    @Autowired
    lateinit var repo: TopicMembershipRepository<UUID>

    @Autowired
    lateinit var byMemberRepo: TopicMembershipByMemberRepository<UUID>

    @Autowired
    lateinit var byMemberOfRepo: TopicMembershipByMemberOfRepository<UUID>

    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    @Test
    fun `membershipOf should not return all`() {
        val membership = TopicMembershipByKey(UUIDs.timeBased(), UUIDs.timeBased(), UUIDs.timeBased())

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
        val membership = TopicMembershipByKey(UUIDs.timeBased(), UUIDs.timeBased(), UUIDs.timeBased())

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
        val keyId = Key.funKey(UUIDs.timeBased())
        val membership = TopicMembershipByKey(keyId.id, UUIDs.timeBased(), UUIDs.timeBased())

        val membershipSave = repo
                .save(membership)
                .thenMany(repo.findByKey(keyId.id))

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
        val memberships = mutableSetOf<TopicMembershipByKey<UUID>>()
        val streamOfMemberships = Flux.generate<UUID> { s -> s.next(UUIDs.timeBased()) }
                .take(2)
                .map { key ->
                    val i = TopicMembershipByKey(
                            key,
                            UUIDs.timeBased(),
                            UUIDs.timeBased())

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

    // TODO Fix this testing bug (cassandraUnit based)
    //@Test
    fun `should save, find by memberId`() {
        val keyId = UUIDs.timeBased()
        val membership = TopicMembershipByMember(
                UUIDs.timeBased(),
                keyId,
                UUIDs.timeBased())


        val membershipSave = byMemberRepo
                .save(membership)
                .thenMany(byMemberRepo.findByMember(keyId))

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

    // TODO Fix this testing bug (cassandraUnit based)
    //@Test
    fun `should save, find by memberOf`() {
        val keyId = UUIDs.timeBased()
        val membership = TopicMembershipByMemberOf(
                UUIDs.timeBased(),
                UUIDs.timeBased(),
                keyId
        )

        val membershipSave = byMemberOfRepo
                .save(membership)
                .thenMany(byMemberOfRepo.findByMemberOf(keyId))

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
                    TopicMembershipByKey(it.id,
                            UUIDs.timeBased(),
                            UUIDs.timeBased())
                })

        val repoStream = repo
                .saveAll(memberships)
                .thenMany(repo.findByKeyIn(keyIdsList))

        StepVerifier
                .create(repoStream)
                .expectSubscription()
                .expectNextCount(5)
                .verifyComplete()
    }
}