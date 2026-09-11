package com.demo.chat.test.repository

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.index.cassandra.impl.KeyValueIndex
import com.demo.chat.index.cassandra.repository.KeyValueIndexByIdRepository
import com.demo.chat.index.cassandra.repository.KeyValueIndexRepository
import com.demo.chat.service.core.KeyValueIndexFields
import com.demo.chat.service.core.KeyValueIndexFieldsEntry
import com.demo.chat.service.core.KeyValueIndexService
import com.demo.chat.service.core.TypedKeyValueIndexFields
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

data class IndexedJob(val status: String)

/**
 * A stored value changes. The index must answer for the current value only.
 *
 * Without this, a job that reached SUCCEEDED still answers a query for
 * RUNNING, because an insert never removes the row of the earlier value.
 */
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [IndexRepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=uuid"])
@Tag("integration")
class KeyValueIndexUpdateTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {

    @Autowired
    lateinit var byFieldRepo: KeyValueIndexRepository<UUID>

    @Autowired
    lateinit var byIdRepo: KeyValueIndexByIdRepository<UUID>

    private val fields = TypedKeyValueIndexFields(
        listOf(
            KeyValueIndexFieldsEntry(
                IndexedJob::class.java,
                KeyValueIndexFields { value -> listOf(Pair("status", (value as IndexedJob).status)) }
            )
        )
    )

    private fun index(): KeyValueIndexService<UUID, Map<String, String>> =
        KeyValueIndex(fields, byFieldRepo, byIdRepo)

    @Test
    fun `a changed value replaces the entry of the earlier value`() {
        val index = index()
        val id = Uuids.timeBased()
        val running = "RUNNING-$id"
        val succeeded = "SUCCEEDED-$id"

        StepVerifier
            .create(
                index.add(KeyValuePair.create(Key.funKey(id), IndexedJob(running)))
                    .then(index.add(KeyValuePair.create(Key.funKey(id), IndexedJob(succeeded))))
                    .thenMany(index.findBy(mapOf("status" to running)))
            )
            .verifyComplete()

        StepVerifier
            .create(index.findBy(mapOf("status" to succeeded)))
            .assertNext { found -> Assertions.assertThat(found.id).isEqualTo(id) }
            .verifyComplete()
    }

    @Test
    fun `a replaced entry leaves another entity alone`() {
        val index = index()
        val first = Uuids.timeBased()
        val second = Uuids.timeBased()
        val shared = "SHARED-$first"

        StepVerifier
            .create(
                index.add(KeyValuePair.create(Key.funKey(first), IndexedJob(shared)))
                    .then(index.add(KeyValuePair.create(Key.funKey(second), IndexedJob(shared))))
                    .then(index.add(KeyValuePair.create(Key.funKey(first), IndexedJob("CHANGED-$first"))))
                    .thenMany(index.findBy(mapOf("status" to shared)))
            )
            .assertNext { found -> Assertions.assertThat(found.id).isEqualTo(second) }
            .verifyComplete()
    }

    // The removal table must not keep the row of a value that is gone, or a
    // later rem would try to delete a partition that no longer holds the id.
    @Test
    fun `a changed value leaves one row in the removal table`() {
        val index = index()
        val id = Uuids.timeBased()

        StepVerifier
            .create(
                index.add(KeyValuePair.create(Key.funKey(id), IndexedJob("RUNNING-$id")))
                    .then(index.add(KeyValuePair.create(Key.funKey(id), IndexedJob("SUCCEEDED-$id"))))
                    .thenMany(byIdRepo.findByKeyId(id))
            )
            .expectNextCount(1)
            .verifyComplete()
    }
}
