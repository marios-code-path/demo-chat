package com.demo.chat.test.index

import com.demo.chat.test.key.TestRoots

import com.demo.chat.test.key.FakeKeyServices

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipal
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipalKey
import com.demo.chat.index.cassandra.domain.AuthMetadataByTarget
import com.demo.chat.index.cassandra.domain.AuthMetadataByTargetKey
import com.demo.chat.index.cassandra.domain.AuthMetadataById
import com.demo.chat.index.cassandra.repository.AuthMetadataByIdRepository
import com.demo.chat.index.cassandra.repository.AuthMetadataByPrincipalRepository
import com.demo.chat.index.cassandra.repository.AuthMetadataByTargetRepository
import com.demo.chat.index.cassandra.impl.AuthMetadataIndex
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.test.anyObject
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

/**
 * The removal contract of the Cassandra grant index, proved against mocks.
 *
 * The primary key of the two index tables is the target or the principal.
 * A removal that named the grant id alone would clear the wrong row, which
 * is the defect in `CHAT-rmxxtwtu`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class AuthMetadataIndexTests {
    private lateinit var index: AuthMetaIndex<UUID, Map<String, String>>

    @MockitoBean
    lateinit var byPrincipalRepo: AuthMetadataByPrincipalRepository<UUID>

    @MockitoBean
    lateinit var byTargetRepo: AuthMetadataByTargetRepository<UUID>

    @MockitoBean
    lateinit var byIdRepo: AuthMetadataByIdRepository<UUID>

    private val keyGenerator: () -> UUID = { UUID.randomUUID() }

    private lateinit var grantId: UUID
    private lateinit var targetId: UUID
    private lateinit var principalId: UUID

    @BeforeEach
    fun setUp() {
        grantId = keyGenerator()
        targetId = keyGenerator()
        principalId = keyGenerator()

        val authMetaPrincipal = AuthMetadataByPrincipal(
            AuthMetadataByPrincipalKey(principalId, grantId),
            targetId,
            TestRoots.UUID_ROOT,
            TestRoots.UUID_ROOT,
            "TEST",
            false,
            System.currentTimeMillis()
        )

        val authMetaTarget = AuthMetadataByTarget(
            AuthMetadataByTargetKey(targetId, grantId),
            principalId,
            TestRoots.UUID_ROOT,
            TestRoots.UUID_ROOT,
            authMetaPrincipal.permission,
            false,
            authMetaPrincipal.expires
        )

        val authMetaById = AuthMetadataById(
            grantId,
            targetId,
            principalId,
            TestRoots.UUID_ROOT,
            TestRoots.UUID_ROOT
        )

        BDDMockito
            .given(byPrincipalRepo.save(anyObject<AuthMetadataByPrincipal<UUID>>()))
            .willReturn(Mono.just(authMetaPrincipal))

        BDDMockito
            .given(byTargetRepo.save(anyObject<AuthMetadataByTarget<UUID>>()))
            .willReturn(Mono.just(authMetaTarget))

        BDDMockito
            .given(byIdRepo.save(anyObject<AuthMetadataById<UUID>>()))
            .willReturn(Mono.just(authMetaById))

        BDDMockito.given(byPrincipalRepo.deleteById(anyObject<AuthMetadataByPrincipalKey<UUID>>()))
            .willReturn(Mono.empty<Void>())

        BDDMockito.given(byTargetRepo.deleteById(anyObject<AuthMetadataByTargetKey<UUID>>()))
            .willReturn(Mono.empty<Void>())

        BDDMockito.given(byIdRepo.deleteById(anyObject<UUID>()))
            .willReturn(Mono.empty<Void>())

        BDDMockito.given(byPrincipalRepo.findByKeyPrincipalId(anyObject()))
            .willReturn(Flux.just(authMetaPrincipal))

        BDDMockito.given(byTargetRepo.findByKeyTargetId(anyObject()))
            .willReturn(Flux.just(authMetaTarget))

        BDDMockito.given(byIdRepo.findByKeyId(anyObject()))
            .willReturn(Flux.just(authMetaById))

        this.index = AuthMetadataIndex(
            UUIDUtil(),
            byTargetRepo,
            byPrincipalRepo,
            byIdRepo,
            FakeKeyServices.uuidRoots()
        )
    }

    @Test
    fun `should save principal`() {
        StepVerifier
            .create(
                index.add(
                    AuthMetadata.create(
                        TestKeys.key(keyGenerator()),
                        TestKeys.key(keyGenerator()),
                        TestKeys.key(keyGenerator()),
                        "TEST",
                        System.currentTimeMillis()
                    )
                )
            )
            .verifyComplete()
    }

    @Test
    fun `a removal clears the row of the grant, not the row of a partition`() {
        StepVerifier
            .create(index.rem(TestKeys.key(grantId)))
            .verifyComplete()

        // The grant id is a clustering column. A removal keyed on the grant id
        // alone would name a partition that holds no row of this grant.
        Mockito.verify(byTargetRepo).deleteById(AuthMetadataByTargetKey(targetId, grantId))
        Mockito.verify(byPrincipalRepo).deleteById(AuthMetadataByPrincipalKey(principalId, grantId))
        Mockito.verify(byIdRepo).deleteById(grantId)
    }

    @Test
    fun `a removal of an unknown grant clears nothing`() {
        BDDMockito.given(byIdRepo.findByKeyId(anyObject<UUID>()))
            .willReturn(Flux.empty())

        StepVerifier
            .create(index.rem(TestKeys.key(keyGenerator())))
            .verifyComplete()

        Mockito.verify(byTargetRepo, Mockito.never()).deleteById(anyObject<AuthMetadataByTargetKey<UUID>>())
        Mockito.verify(byPrincipalRepo, Mockito.never()).deleteById(anyObject<AuthMetadataByPrincipalKey<UUID>>())
    }

    @Test
    fun `should query by principal`() {
        StepVerifier
            .create(
                index.findBy(mapOf(Pair(AuthMetaIndex.PRINCIPAL, keyGenerator().toString())))
            )
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()
            }
            .verifyComplete()
    }

    @Test
    fun `should query by target`() {
        StepVerifier
            .create(
                index.findBy(mapOf(Pair(AuthMetaIndex.TARGET, keyGenerator().toString())))
            )
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()
            }
            .verifyComplete()
    }
}
