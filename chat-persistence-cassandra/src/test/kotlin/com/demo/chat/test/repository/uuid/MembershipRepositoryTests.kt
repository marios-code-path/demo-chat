package com.demo.chat.test.repository.uuid

import com.demo.chat.domain.Key
import com.demo.chat.persistence.cassandra.domain.TopicMembershipByKey
import com.demo.chat.persistence.cassandra.repository.TopicMembershipRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestUUIDKeyGenerator
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.service.core.key=uuid"])
class MembershipRepositoryTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {

    @Autowired
    lateinit var repo: TopicMembershipRepository<UUID>

    @Test
    fun `should save, find all`() {
        val membership = TopicMembershipByKey(keyGenerator.nextKey(), keyGenerator.nextKey(), keyGenerator.nextKey())

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
        val keyId = Key.funKey(keyGenerator.nextKey())
        val membership = TopicMembershipByKey(keyId.id, keyGenerator.nextKey(), keyGenerator.nextKey())

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
        val streamOfMemberships = Flux.generate<UUID> { s -> s.next(keyGenerator.nextKey()) }
            .take(2)
            .map { key ->
                val i = TopicMembershipByKey(
                    key,
                    keyGenerator.nextKey(),
                    keyGenerator.nextKey()
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

    @Test
    fun `should save many find by Ids`() {
        val keyList = Stream.generate {
            Key.funKey(keyGenerator.nextKey())
        }.limit(5).collect(Collectors.toList())

        val keyIdsList = keyList.stream().map { it.id }.toList()
        val memberships = Flux.fromIterable(keyList
            .map {
                TopicMembershipByKey(
                    it.id,
                    keyGenerator.nextKey(),
                    keyGenerator.nextKey()
                )
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