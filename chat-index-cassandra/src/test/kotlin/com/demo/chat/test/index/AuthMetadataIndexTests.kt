package com.demo.chat.test.index

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipal
import com.demo.chat.index.cassandra.domain.AuthMetadataByTarget
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
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class AuthMetadataIndexTests {
    private lateinit var index: AuthMetaIndex<UUID, Map<String, String>>

    @MockBean
    lateinit var byPrincipalRepo: AuthMetadataByPrincipalRepository<UUID>

    @MockBean
    lateinit var byTargetRepo: AuthMetadataByTargetRepository<UUID>

    private val keyGenerator: () -> UUID = { UUID.randomUUID() }

    @BeforeEach
    fun setUp() {

        val authMetaPrincipal = AuthMetadataByPrincipal(
            keyGenerator(),
            keyGenerator(),
            keyGenerator(),
            "TEST",
            System.currentTimeMillis()
        )

        val authMetaTarget = AuthMetadataByTarget(
            authMetaPrincipal.keyId,
            authMetaPrincipal.targetId,
            authMetaPrincipal.principalId,
            authMetaPrincipal.permission,
            authMetaPrincipal.expires
        )

        BDDMockito
            .given(byPrincipalRepo.save(anyObject<AuthMetadataByPrincipal<UUID>>()))
            .willReturn(Mono.just(authMetaPrincipal))

        BDDMockito
            .given(byTargetRepo.save(anyObject<AuthMetadataByTarget<UUID>>()))
            .willReturn(Mono.just(authMetaTarget))

        BDDMockito
            .given(byPrincipalRepo.delete(anyObject<AuthMetadataByPrincipal<UUID>>()))
            .willReturn(Mono.empty())

        BDDMockito.given(byTargetRepo.delete(anyObject<AuthMetadataByTarget<UUID>>()))
            .willReturn(Mono.empty())

        BDDMockito.given(byPrincipalRepo.findByPrincipalId(anyObject()))
            .willReturn(Flux.just(authMetaPrincipal))

        BDDMockito.given(byTargetRepo.findByTargetId(anyObject()))
            .willReturn(Flux.just(authMetaTarget))

        this.index = AuthMetadataIndex(UUIDUtil(), byTargetRepo, byPrincipalRepo)

    }


    @Test
    fun `should save principal`() {
        StepVerifier
            .create(
                index.add(
                    AuthMetadata.create(
                        Key.funKey(keyGenerator()),
                        Key.funKey(keyGenerator()),
                        Key.funKey(keyGenerator()),
                        "TEST",
                        System.currentTimeMillis()
                    )
                )
            )
            .verifyComplete()
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