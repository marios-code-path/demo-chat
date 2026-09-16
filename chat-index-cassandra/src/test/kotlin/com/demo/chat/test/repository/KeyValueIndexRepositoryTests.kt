package com.demo.chat.test.repository

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndex
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndexById
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndexByIdKey
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndexKey
import com.demo.chat.index.cassandra.repository.KeyValueIndexByIdRepository
import com.demo.chat.index.cassandra.repository.KeyValueIndexRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.IndexRepositoryTestConfiguration
import com.demo.chat.test.TestUUIDKeyGenerator
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import java.util.UUID

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [IndexRepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class KeyValueIndexRepositoryTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {

    @Autowired
    lateinit var byFieldRepo: KeyValueIndexRepository<UUID>

    @Autowired
    lateinit var byIdRepo: KeyValueIndexByIdRepository<UUID>

    @Test
    fun `should save and find by field and value`() {
        val id = Uuids.timeBased()
        val value = "client-${id}"

        val composed = byFieldRepo
            .save(ChatKeyValueIndex(ChatKeyValueIndexKey("client_id", value, id)))
            .thenMany(byFieldRepo.findByKeyFieldAndKeyValue("client_id", value))

        StepVerifier
            .create(composed)
            .assertNext { row -> Assertions.assertThat(row.key.id).isEqualTo(id) }
            .verifyComplete()
    }

    // Two entities under one field value share a partition. Both must return.
    @Test
    fun `should find every entity under one field value`() {
        val first = Uuids.timeBased()
        val second = Uuids.timeBased()
        val value = "shared-${first}"

        val composed = byFieldRepo
            .save(ChatKeyValueIndex(ChatKeyValueIndexKey("root_key", value, first)))
            .then(byFieldRepo.save(ChatKeyValueIndex(ChatKeyValueIndexKey("root_key", value, second))))
            .thenMany(byFieldRepo.findByKeyFieldAndKeyValue("root_key", value))

        StepVerifier
            .create(composed)
            .expectNextCount(2)
            .verifyComplete()
    }

    // The by-id table is what makes rem possible. It must return every row
    // that one entity wrote.
    @Test
    fun `should find every row of one entity by id`() {
        val id = Uuids.timeBased()

        val composed = byIdRepo
            .save(ChatKeyValueIndexById(ChatKeyValueIndexByIdKey(id, "client_id", "abc")))
            .then(byIdRepo.save(ChatKeyValueIndexById(ChatKeyValueIndexByIdKey(id, "name", "a name"))))
            .thenMany(byIdRepo.findByKeyId(id))

        StepVerifier
            .create(composed)
            .expectNextCount(2)
            .verifyComplete()
    }

    // A repeated add writes the same primary key. Cassandra overwrites it, so
    // the index must hold one row, not two.
    @Test
    fun `a repeated save holds one row`() {
        val id = Uuids.timeBased()
        val value = "repeat-${id}"

        val composed = byFieldRepo
            .save(ChatKeyValueIndex(ChatKeyValueIndexKey("client_id", value, id)))
            .then(byFieldRepo.save(ChatKeyValueIndex(ChatKeyValueIndexKey("client_id", value, id))))
            .thenMany(byFieldRepo.findByKeyFieldAndKeyValue("client_id", value))

        StepVerifier
            .create(composed)
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    fun `should delete a row`() {
        val id = Uuids.timeBased()
        val value = "delete-${id}"
        val row = ChatKeyValueIndex(ChatKeyValueIndexKey("client_id", value, id))

        val composed = byFieldRepo
            .save(row)
            .then(byFieldRepo.delete(row))
            .thenMany(byFieldRepo.findByKeyFieldAndKeyValue("client_id", value))

        StepVerifier
            .create(composed)
            .verifyComplete()
    }
}
