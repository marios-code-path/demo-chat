package com.demo.chat.test.persistence.integration

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.User
import com.demo.chat.persistence.cassandra.impl.KeyServiceCassandra
import com.demo.chat.persistence.cassandra.impl.KeyValuePersistenceCassandra
import com.demo.chat.persistence.cassandra.repository.KeyValuePairRepository
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestLongKeyService
import com.demo.chat.test.repository.RepositoryTestConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.service.core.kv", "app.key.type=long"])
class KeyValuePersistenceIntegrationTests : CassandraSchemaTest<Long>(TestLongKeyService()) {

    lateinit var persistence: KeyValueStore<Long, Any>

    @Autowired
    lateinit var repo: KeyValuePairRepository<Long>

    @Autowired
    lateinit var mapper: ObjectMapper

    @BeforeAll
    fun setUp() {
        this.persistence = KeyValuePersistenceCassandra( KeyServiceCassandra(template, keyGenerator), repo, mapper)
    }

    fun kvAsserts(kv: KeyValuePair<Long, Any>) {
        Assertions
            .assertThat(kv)
            .isNotNull

        Assertions
            .assertThat(kv.data)
            .isNotNull
            .hasFieldOrPropertyWithValue("handle", "test")
    }

    @Test
    fun `should byIds`() {
        val key1 = Key.funKey(1L)
        val key2 = Key.funKey(2L)
        val userObject = User.create(key1, "test", "test", "test")
        val userObject2 = User.create(key2, "test1", "test1", "test1")

        val addOp = persistence.add(KeyValuePair.create(Key.funKey(1L), userObject))
            .then(persistence.add(KeyValuePair.create(Key.funKey(2L), userObject2)))

        StepVerifier
            .create(addOp)
            .expectSubscription()
            .verifyComplete()

        StepVerifier.create(
            persistence.typedByIds(listOf(key1, key2), User::class.java)
        )
            .expectNextCount(2)
            .verifyComplete()
    }

    @Test
    fun `should store findAll`() {
        val userObject = User.create(Key.funKey(1L), "test", "test", "test")
        val userObject2 = User.create(Key.funKey(2L), "test1", "test1", "test1")

        val addOp = persistence.add(KeyValuePair.create(Key.funKey(1L), userObject))
            .then(persistence.add(KeyValuePair.create(Key.funKey(2L), userObject2)))

        StepVerifier
            .create(addOp)
            .expectSubscription()
            .verifyComplete()

        val findOp = persistence.typedAll(User::class.java)

        StepVerifier
            .create(findOp)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .extracting("data")
                    .hasFieldOrProperty("handle")
            }
            .expectNextCount(1)
            .verifyComplete()

        StepVerifier.create(persistence.all())
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .extracting("data")
                    .isInstanceOf(String::class.java)
            }
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    fun `should store find`() {
        val userObject = User.create(Key.funKey(1L), "test", "test", "test")

        val addOp = persistence.add(KeyValuePair.create(Key.funKey(1L), userObject))

        StepVerifier
            .create(addOp)
            .expectSubscription()
            .verifyComplete()

        val findOp = persistence.typedGet(Key.funKey(1L), User::class.java)

        StepVerifier
            .create(findOp)
            .assertNext {
                kvAsserts(it)
            }
            .verifyComplete()


        StepVerifier.create(persistence.get(Key.funKey(1L)))
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .extracting("data")
                    .isInstanceOf(String::class.java)
            }
            .verifyComplete()
    }
}