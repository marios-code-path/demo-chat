package com.demo.chat.test.repository.long

import com.demo.chat.persistence.cassandra.domain.CredKey
import com.demo.chat.persistence.cassandra.domain.KeyCredentialById
import com.demo.chat.persistence.cassandra.repository.KeyCredentialRepository
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
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=long"])
class LKeyCredentialRepositoryTests :  CassandraSchemaTest<Long>(TestLongKeyGenerator()) {

    @Autowired
    private lateinit var repo: KeyCredentialRepository<Long>

    @Test
    fun `save and find key cred`() {
        val keyId = keyGenerator.nextId()
        val keyCred = KeyCredentialById(CredKey(keyId, "CREDENTIAL"), "SECRET")

        val saveStream = repo.save(keyCred)

        val compositeStream = Flux.from(saveStream)
            .thenMany(repo.findByKeyId(keyId))

        StepVerifier.create(compositeStream)
            .assertNext { k ->
                Assertions.assertThat(k)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("data", "SECRET")
            }
            .verifyComplete()
    }
}