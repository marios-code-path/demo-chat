package com.demo.chat.test.repository

import com.demo.chat.test.key.TestRoots

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipal
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipalKey
import com.demo.chat.index.cassandra.domain.AuthMetadataByTarget
import com.demo.chat.index.cassandra.domain.AuthMetadataByTargetKey
import com.demo.chat.index.cassandra.impl.AuthMetadataIndex
import com.demo.chat.index.cassandra.repository.AuthMetadataByIdRepository
import com.demo.chat.index.cassandra.repository.AuthMetadataByPrincipalRepository
import com.demo.chat.index.cassandra.repository.AuthMetadataByTargetRepository
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.IndexRepositoryTestConfiguration
import com.demo.chat.test.TestUUIDKeyGenerator
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
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

/**
 * One target holds many grants, and one principal holds many grants.
 *
 * The primary key of each index table carries the grant id as a clustering
 * column. Before this table shape, a second grant on one target replaced the
 * first row. `CHAT-rmxxtwtu` holds that defect.
 *
 * The removal proof runs through the production index, because the index owns
 * the by-id lookup that names the partitions of a removed grant.
 */
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [IndexRepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class AuthMetadataIndexRepositoryTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {
    @Autowired
    lateinit var byPrincipalRepo: AuthMetadataByPrincipalRepository<UUID>

    @Autowired
    lateinit var byTargetRepo: AuthMetadataByTargetRepository<UUID>

    @Autowired
    lateinit var byIdRepo: AuthMetadataByIdRepository<UUID>

    private val index by lazy {
        AuthMetadataIndex(UUIDUtil(), byTargetRepo, byPrincipalRepo, byIdRepo, FakeKeyServices.uuidRoots())
    }

    private fun grant(principal: Key<UUID>, target: Key<UUID>): AuthMetadata<UUID> =
        AuthMetadata.create(TestKeys.key(keyGenerator.nextId()), principal, target, "GET", false, Long.MAX_VALUE)

    private fun grantsAgainst(target: Key<UUID>): Flux<UUID> =
        index.findBy(mapOf(AuthMetaIndex.TARGET to target.id.toString())).map { it.id }

    private fun grantsOf(principal: Key<UUID>): Flux<UUID> =
        index.findBy(mapOf(AuthMetaIndex.PRINCIPAL to principal.id.toString())).map { it.id }

    @Test
    fun shouldContextLoad() {
        Assertions
            .assertThat(template)
            .describedAs("Reactive Template Exists")
            .isNotNull
    }

    @Test
    fun `one target keeps every grant of that target`() {
        val target = TestKeys.key(keyGenerator.nextId())
        val first = grant(TestKeys.key(keyGenerator.nextId()), target)
        val second = grant(TestKeys.key(keyGenerator.nextId()), target)

        StepVerifier
            .create(
                index.add(first)
                    .then(index.add(second))
                    .thenMany(grantsAgainst(target).collectList())
            )
            .assertNext { ids ->
                Assertions.assertThat(ids).containsExactlyInAnyOrder(first.key.id, second.key.id)
            }
            .verifyComplete()
    }

    @Test
    fun `removing one grant leaves the other grant of that target`() {
        val target = TestKeys.key(keyGenerator.nextId())
        val first = grant(TestKeys.key(keyGenerator.nextId()), target)
        val second = grant(TestKeys.key(keyGenerator.nextId()), target)

        StepVerifier
            .create(
                index.add(first)
                    .then(index.add(second))
                    .then(index.rem(first.key))
                    .thenMany(grantsAgainst(target).collectList())
            )
            .assertNext { ids ->
                Assertions.assertThat(ids).containsExactly(second.key.id)
            }
            .verifyComplete()
    }

    @Test
    fun `one principal keeps every grant of that principal`() {
        val principal = TestKeys.key(keyGenerator.nextId())
        val first = grant(principal, TestKeys.key(keyGenerator.nextId()))
        val second = grant(principal, TestKeys.key(keyGenerator.nextId()))

        StepVerifier
            .create(
                index.add(first)
                    .then(index.add(second))
                    .thenMany(grantsOf(principal).collectList())
            )
            .assertNext { ids ->
                Assertions.assertThat(ids).containsExactlyInAnyOrder(first.key.id, second.key.id)
            }
            .verifyComplete()
    }

    @Test
    fun `removing one grant leaves the other grant of that principal`() {
        val principal = TestKeys.key(keyGenerator.nextId())
        val first = grant(principal, TestKeys.key(keyGenerator.nextId()))
        val second = grant(principal, TestKeys.key(keyGenerator.nextId()))

        StepVerifier
            .create(
                index.add(first)
                    .then(index.add(second))
                    .then(index.rem(first.key))
                    .thenMany(grantsOf(principal).collectList())
            )
            .assertNext { ids ->
                Assertions.assertThat(ids).containsExactly(second.key.id)
            }
            .verifyComplete()
    }

    @Test
    fun `a removal clears every row of the supplied grant`() {
        val principal = TestKeys.key(keyGenerator.nextId())
        val target = TestKeys.key(keyGenerator.nextId())
        val only = grant(principal, target)

        StepVerifier
            .create(
                index.add(only)
                    .then(index.rem(only.key))
                    .thenMany(byIdRepo.findByKeyId(only.key.id))
            )
            .verifyComplete()

        StepVerifier
            .create(byTargetRepo.findByKeyTargetId(target.id))
            .verifyComplete()

        StepVerifier
            .create(byPrincipalRepo.findByKeyPrincipalId(principal.id))
            .verifyComplete()
    }

    @Test
    fun `the by-id row carries the target and the principal of its grant`() {
        val principal = TestKeys.key(keyGenerator.nextId())
        val target = TestKeys.key(keyGenerator.nextId())
        val only = grant(principal, target)

        StepVerifier
            .create(
                index.add(only)
                    .thenMany(byIdRepo.findByKeyId(only.key.id))
            )
            .assertNext { row ->
                Assertions.assertThat(row.targetId).isEqualTo(target.id)
                Assertions.assertThat(row.principalId).isEqualTo(principal.id)
                Assertions.assertThat(row.targetRoot).isEqualTo(TestRoots.UUID_ROOT)
            }
            .verifyComplete()
    }

    @Test
    fun `byTargetRepo should save and find by the target partition`() {
        val grantId = TestKeys.key(keyGenerator.nextId())
        val targetId = keyGenerator.nextId()
        val authMeta = AuthMetadataByTarget(
            AuthMetadataByTargetKey(targetId, grantId.id),
            keyGenerator.nextId(),
            TestRoots.UUID_ROOT,
            TestRoots.UUID_ROOT,
            "TEST",
            false,
            System.currentTimeMillis()
        )

        StepVerifier
            .create(
                byTargetRepo.save(authMeta)
                    .thenMany(byTargetRepo.findByKeyTargetId(authMeta.key.targetId))
            )
            .assertNext {
                Assertions.assertThat(it.key.id).isEqualTo(grantId.id)
            }
            .verifyComplete()
    }

    @Test
    fun `byPrincipalRepo should save and find by the principal partition`() {
        val grantId = TestKeys.key(keyGenerator.nextId())
        val principalId = keyGenerator.nextId()
        val authMeta = AuthMetadataByPrincipal(
            AuthMetadataByPrincipalKey(principalId, grantId.id),
            keyGenerator.nextId(),
            TestRoots.UUID_ROOT,
            TestRoots.UUID_ROOT,
            "TEST",
            false,
            System.currentTimeMillis()
        )

        StepVerifier
            .create(
                byPrincipalRepo.save(authMeta)
                    .thenMany(byPrincipalRepo.findByKeyPrincipalId(authMeta.key.principalId))
            )
            .assertNext {
                Assertions.assertThat(it.key.id).isEqualTo(grantId.id)
            }
            .verifyComplete()
    }
}
