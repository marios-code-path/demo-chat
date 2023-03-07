package com.demo.chat.test.persistence

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
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class AuthMetadataPersistenceTests {
    lateinit var authMetadataPersistence: AuthMetaPersistence<UUID>

    @MockBean
    lateinit var repo: AuthMetadataRepository<UUID>

    private val keyService = TestUUIDKeyService()

    private val testAuthMetadata = AuthMetadataById(
        AuthMetadataIdKey(keyService.nextId()),
        keyService.nextId(),
        keyService.nextId(),
        "TEST",
        System.currentTimeMillis()
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

        authMetadataPersistence = AuthMetaPersistenceCassandra(keyService, repo)
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
            .add(testAuthMetadata)
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