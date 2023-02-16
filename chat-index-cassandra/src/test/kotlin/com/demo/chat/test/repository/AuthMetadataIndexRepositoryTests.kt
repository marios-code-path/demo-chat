package com.demo.chat.test.repository

import com.demo.chat.domain.Key
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipal
import com.demo.chat.index.cassandra.domain.AuthMetadataByTarget
import com.demo.chat.index.cassandra.repository.AuthMetadataByPrincipalRepository
import com.demo.chat.index.cassandra.repository.AuthMetadataByTargetRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.IndexRepositoryTestConfiguration
import com.demo.chat.test.TestUUIDKeyGenerator
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [IndexRepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=uuid"])
class AuthMetadataIndexRepositoryTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {
    @Autowired
    lateinit var byPrincipalRepo: AuthMetadataByPrincipalRepository<UUID>

    @Autowired
    lateinit var byTargetRepo: AuthMetadataByTargetRepository<UUID>

    @Test
    fun shouldContextLoad() {
        Assertions
            .assertThat(template)
            .describedAs("Reactive Template Exists")
            .isNotNull
    }


    @Test
    fun `byPrincipal should not return all`() {
        val authMeta = AuthMetadataByPrincipal(
            keyGenerator.nextKey(),
            keyGenerator.nextKey(),
            keyGenerator.nextKey(),
            "TEST",
            System.currentTimeMillis()
        )

        val membershipSave = byPrincipalRepo
            .save(authMeta)
            .thenMany(byPrincipalRepo.findAll())

        StepVerifier
            .create(membershipSave)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("permission", "TEST")
            }
            .verifyComplete()
    }

    @Test
    fun `byPrincipalRepo should save, find by Principal`() {
        val keyId = Key.funKey(keyGenerator.nextKey())
        val authMeta = AuthMetadataByPrincipal(
            keyId.id,
            keyGenerator.nextKey(),
            keyGenerator.nextKey(),
            "TEST",
            System.currentTimeMillis()
        )

        val authMetaSave = byPrincipalRepo
            .save(authMeta)
            .thenMany(byPrincipalRepo.findByPrincipalId(authMeta.principal.id))

        StepVerifier
            .create(authMetaSave)
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
            }
            .verifyComplete()
    }

    @Test
    fun `byTargetRepo should save, find by Target`() {
        val keyId = Key.funKey(keyGenerator.nextKey())
        val authMeta = AuthMetadataByTarget(
            keyId.id,
            keyGenerator.nextKey(),
            keyGenerator.nextKey(),
            "TEST",
            System.currentTimeMillis()
        )

        val authMetaSave = byTargetRepo
            .save(authMeta)
            .thenMany(byTargetRepo.findByTargetId(authMeta.target.id))

        StepVerifier
            .create(authMetaSave)
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
            }
            .verifyComplete()
    }
}