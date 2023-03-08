package com.demo.chat.test.repository.long

import com.demo.chat.persistence.cassandra.domain.AuthMetadataById
import com.demo.chat.persistence.cassandra.domain.AuthMetadataIdKey
import com.demo.chat.persistence.cassandra.repository.AuthMetadataRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=long"])
class LAuthMetadataRepositoryTests : CassandraSchemaTest<Long>(TestLongKeyGenerator()) {

    @Autowired
    private lateinit var repository: AuthMetadataRepository<Long>

    @Test
    fun `save test`() {
        Hooks.onOperatorDebug()

        val authmeta = AuthMetadataById(
            AuthMetadataIdKey(keyGenerator.nextId()),
            keyGenerator.nextId(),
            keyGenerator.nextId(),
            "TEST",
            false,
            System.currentTimeMillis()
        )

        val authmetadataSave = repository
            .save(authmeta)
            .checkpoint()

        StepVerifier
            .create(authmetadataSave)
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("permission", "TEST")
            }
            .verifyComplete()
    }

    @Test
    fun `save and find all`() {
        Hooks.onOperatorDebug()

        val authmeta = AuthMetadataById(
            AuthMetadataIdKey(keyGenerator.nextId()),
            keyGenerator.nextId(),
            keyGenerator.nextId(),
            "TEST",
            false,
            System.currentTimeMillis()
        )

        val save = repository.save(authmeta)

        val findById = repository.findAll()

        val composed = Mono.from(save)
            .thenMany(findById)

        StepVerifier
            .create(composed)
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("permission", "TEST")
            }
            .verifyComplete()
    }

    @Test
    fun `save and find by key id`() {
        Hooks.onOperatorDebug()

        val authmeta = AuthMetadataById(
            AuthMetadataIdKey(keyGenerator.nextId()),
            keyGenerator.nextId(),
            keyGenerator.nextId(),
            "TEST",
            false,
            System.currentTimeMillis()
        )

        val save = repository.save(authmeta)

        val findById = repository.findByKeyId(authmeta.key.id)

        val composed = Mono.from(save)
            .then(findById)

        StepVerifier
            .create(composed)
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("permission", "TEST")
            }
            .verifyComplete()
    }
}