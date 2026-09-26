package com.demo.chat.test.persistence.integration

import com.demo.chat.test.key.FakeKeyServices

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.persistence.cassandra.impl.KeyValuePersistenceCassandra
import com.demo.chat.persistence.cassandra.repository.KeyValuePairRepository
import com.demo.chat.service.vector.IndexJobCodec
import com.demo.chat.test.TestLongKeyService
import com.demo.chat.test.repository.RepositoryTestConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * The cassandra shape. The kv_pair table stores text, so the codec takes its
 * string branch.
 */
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=long"])
@Tag("integration")
class CassandraIndexJobDecodeTests {

    @Autowired
    lateinit var repo: KeyValuePairRepository<Long>

    // The deployed mapper. TestObjectMapperConfiguration supplies it, and
    // RepositoryTestConfiguration imports that class.
    @Autowired
    lateinit var mapper: ObjectMapper

    private val ids = AtomicLong(9_000L)

    private fun codec() = IndexJobCodec<Long>(mapper)

    private fun store() = KeyValuePersistenceCassandra(TestLongKeyService(), FakeKeyServices.longRoots(), repo, mapper)

    @Test
    fun `a stored job reads back through the codec`() {
        val store = store()
        val key = TestKeys.key(ids.incrementAndGet())
        val job = IndexJob(
            key = key,
            nodeId = 7,
            keyType = "long",
            incarnationId = "incarnation-a",
            startedBy = TestKeys.key(ids.incrementAndGet()),
            startedAt = Instant.parse("2026-09-12T12:00:00Z"),
            outcome = JobOutcome.FAILED,
            failed = 2L,
        )

        store.add(KeyValuePair.create(key, job as Any)).block()
        val stored = store.get(key).block()!!

        Assertions.assertThat(stored.data).isInstanceOf(String::class.java)

        val decoded = codec().decode(stored.data)

        Assertions.assertThat(decoded.outcome).isEqualTo(JobOutcome.FAILED)
        Assertions.assertThat(decoded.failed).isEqualTo(2L)
        Assertions.assertThat(decoded.covers).isFalse()
    }
}
