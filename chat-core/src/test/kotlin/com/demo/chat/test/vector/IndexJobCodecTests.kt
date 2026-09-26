package com.demo.chat.test.vector

import com.demo.chat.test.key.TestKeys

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.service.vector.IndexJobCodec
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Each backend hands the store a different shape. The memory store returns the
 * object, redis returns a map, and cassandra returns the JSON string it stored.
 */
class IndexJobCodecTests {
    // The same modules the deployments register. A bare mapper cannot read a
    // Key, which is an interface with no type information on the wire.
    private val mapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .apply { registerModules(DefaultChatJacksonModules().allModules()) }
    private val codec = IndexJobCodec<Long>(mapper)

    private val job = IndexJob(
        key = TestKeys.key(500L),
        topicKey = TestKeys.key(900900L),
        nodeId = 7,
        keyType = "long",
        embeddingIdentity = "acme-e5",
        incarnationId = "incarnation-a",
        startedBy = TestKeys.key(1000L),
        startedAt = Instant.parse("2026-09-12T12:00:00Z"),
        outcome = JobOutcome.SUCCEEDED,
        indexed = 3L,
        invalidationCount = 1L,
    )

    @Test
    fun `a stored object passes through`() {
        Assertions.assertThat(codec.decode(job)).isEqualTo(job)
    }

    @Test
    fun `a job survives a json string round trip`() {
        val decoded = codec.decode(mapper.writeValueAsString(job))

        Assertions.assertThat(decoded.key.id).isEqualTo(500L)
        Assertions.assertThat(decoded.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(decoded.indexed).isEqualTo(3L)
        Assertions.assertThat(decoded.invalidationCount).isEqualTo(1L)
        Assertions.assertThat(decoded.startedAt).isEqualTo(job.startedAt)
        Assertions.assertThat(decoded.covers).isFalse()
    }

    @Test
    fun `a job survives a map round trip`() {
        val asMap = mapper.convertValue(job, Map::class.java)

        val decoded = codec.decode(asMap)

        Assertions.assertThat(decoded.nodeId).isEqualTo(7)
        Assertions.assertThat(decoded.keyType).isEqualTo("long")
        Assertions.assertThat(decoded.incarnationId).isEqualTo("incarnation-a")
        Assertions.assertThat(decoded.outcome).isEqualTo(JobOutcome.SUCCEEDED)
    }

    @Test
    fun `an unknown shape names the runtime class`() {
        Assertions.assertThatThrownBy { codec.decode(42) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("java.lang.Integer")
    }

    @Test
    fun `a legacy json string decodes with a null identity`() {
        // A record written before the field existed carries no key for it.
        // Null states that the writer named no model. That is the fact the
        // coverage filter reads.
        //
        // The fixture is derived rather than written by hand. This mapper
        // already round trips `job` in the test above, so removing one field
        // from its own output cannot disagree with the real wire shape. A
        // hand written literal could, because Key carries a wrapper object and
        // the path to an id is one level deeper than its field name.
        val legacy = mapper.writeValueAsString(withoutIdentity())

        Assertions.assertThat(codec.decode(legacy).embeddingIdentity).isNull()
    }

    @Test
    fun `a legacy map decodes with a null identity`() {
        Assertions.assertThat(codec.decode(withoutIdentity()).embeddingIdentity).isNull()
    }

    /**
     * The map shape of [job], with the new field removed.
     *
     * Redis hands the codec a map after its JSON round trip, and cassandra
     * hands it the JSON string. So one fixture serves both tests.
     */
    private fun withoutIdentity(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val asMap = mapper.convertValue(job, Map::class.java) as Map<String, Any?>
        return asMap.filterKeys { it != "embeddingIdentity" }
    }
}
