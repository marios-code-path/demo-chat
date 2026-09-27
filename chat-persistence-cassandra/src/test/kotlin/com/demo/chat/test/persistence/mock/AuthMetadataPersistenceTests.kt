package com.demo.chat.test.persistence.mock

import com.demo.chat.domain.Key

import com.demo.chat.domain.AuthMetadata

import com.demo.chat.test.key.TestRoots

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.test.key.FakeKeyServices

import com.demo.chat.persistence.cassandra.domain.AuthMetadataById
import com.demo.chat.persistence.cassandra.domain.AuthMetadataIdKey
import com.demo.chat.persistence.cassandra.repository.AuthMetadataRepository
import com.demo.chat.persistence.cassandra.impl.AuthMetaPersistenceCassandra
import com.demo.chat.service.security.AuthMetaPersistence
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestUUIDKeyService
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class AuthMetadataPersistenceTests {
    lateinit var authMetadataPersistence: AuthMetaPersistence<UUID>

    @MockitoBean
    lateinit var repo: AuthMetadataRepository<UUID>

    private val keyService = TestUUIDKeyService()

    private val roots = FakeKeyServices.uuidRoots()

    private val testAuthMetadata = AuthMetadataById(
        AuthMetadataIdKey(keyService.nextId()),
        keyService.nextId(),
        keyService.nextId(),
        roots.of(ChatDomain.USER).id,
        roots.of(ChatDomain.USER).id,
        "TEST",
        false,
        System.currentTimeMillis()
    )

    /** The domain grant of [testAuthMetadata]. A store adds a domain object and maps it to a row. */
    private val testGrant = AuthMetadata.create(
        Key.of(testAuthMetadata.key.id, roots.of(ChatDomain.AUTH_METADATA).id),
        Key.of(testAuthMetadata.principalId, testAuthMetadata.principalRoot),
        Key.of(testAuthMetadata.targetId, testAuthMetadata.targetRoot),
        testAuthMetadata.permission,
        testAuthMetadata.mute,
        testAuthMetadata.expires,
    )

    @BeforeEach
    fun setUp() {
        BDDMockito
            .given(repo.findByKeyId(TestBase.anyObject()))
            .willReturn(Mono.just(testAuthMetadata))

        BDDMockito
            .given(repo.save(TestBase.anyObject()))
            .willReturn(Mono.empty<AuthMetadataById<UUID>>())

        BDDMockito
            .given(repo.findAll())
            .willReturn(Flux.just(testAuthMetadata))

//        BDDMockito
//            .given(repo.findByKeyIn(TestBase.anyObject()))
//            .willReturn(Flux.just(testAuthMetadata))

        BDDMockito
            .given(repo.deleteById(Mockito.any(UUID::class.java)))
            .willReturn(Mono.empty())

        authMetadataPersistence = AuthMetaPersistenceCassandra(keyService, roots, repo)
    }

    @Test
    fun `gets all authmetadata`() {
        val allMemberships = authMetadataPersistence.all()

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
        val saveNFind = authMetadataPersistence
            .add(testGrant)
            .thenMany(authMetadataPersistence.all())

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

}