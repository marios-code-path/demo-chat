package com.demo.chat.test.repository

import com.demo.chat.domain.Key
import com.demo.chat.domain.cassandra.TopicMembershipByKey
import com.demo.chat.repository.cassandra.TopicMembershipRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.CassandraTestConfiguration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*
import java.util.stream.Stream
import kotlin.streams.toList
import com.datastax.oss.driver.api.core.uuid.Uuids as UUIDs

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = [CassandraTestConfiguration::class])
class MembershipRepositoryTests : CassandraSchemaTest() {
    @Autowired
    lateinit var repo: TopicMembershipRepository<UUID>

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



    @Test
    fun `should save many find by Ids`() {
        val keyList = Stream.generate {
            Key.funKey(UUIDs.timeBased())
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