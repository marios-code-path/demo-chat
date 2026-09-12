package com.demo.chat.test.vector

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
        key = Key.funKey(500L),
        nodeId = 7,
        keyType = "long",
        incarnationId = "incarnation-a",
        startedBy = Key.funKey(1000L),
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
}
