package com.demo.chat.test.persistence

import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import com.demo.chat.persistence.cassandra.impl.KeyServiceCassandra
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestUUIDKeyGenerator
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.service.core.key=uuid"])
class KeyServiceTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {
    lateinit var svc: IKeyService<UUID>

    @Value("\${app.service.core.key:uuid}")
    private lateinit var keyType: String

    @BeforeAll
    fun setUp() {
        println("THE KEYTYPE FOR KeyServiceTests IS ${keyType}")
        this.svc = KeyServiceCassandra(template, keyGenerator)
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