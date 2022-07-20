package com.demo.chat.test.persistence

import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.KeyServiceCassandra
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestUUIDKeyGenerator
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.Resource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
class KeyServiceTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {
    lateinit var svc: IKeyService<UUID>

    @Value("classpath:keyspace-\${keyType}.cql")
    override lateinit var cqlFile: Resource

    @BeforeAll
    fun setUp() {
        this.svc = KeyServiceCassandra(template, keyGenerator::nextKey)
    }

    @Test
    fun `created key should Exist`() {
        val keyStream = svc
            .key(User::class.java)
            .flatMap(svc::exists)

        StepVerifier
            .create(keyStream)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isTrue()
            }
            .verifyComplete()
    }

    @Test
    fun `should create an key`() {
        val key = svc.key(User::class.java)

        StepVerifier
            .create(key)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .hasNoNullFieldsOrProperties()
                    .hasFieldOrProperty("id")
            }
            .verifyComplete()
    }

    @Test
    fun `should delete a key`() {
        val key = svc.key(User::class.java)
        val deleteStream = Flux
            .from(key)
            .flatMap(svc::rem)

        StepVerifier
            .create(deleteStream)
            .expectSubscription()
            .verifyComplete()
    }
}