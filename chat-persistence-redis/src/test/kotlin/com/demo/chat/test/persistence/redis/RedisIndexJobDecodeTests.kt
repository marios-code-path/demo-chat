package com.demo.chat.test.persistence.redis

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.persistence.redis.impl.KeyValuePersistenceRedis
import com.demo.chat.service.vector.IndexJobCodec
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Instant
import java.util.UUID

/**
 * The redis shape. A JSON round trip returns the value as a map, so the codec
 * takes its map branch.
 */
@Extensions(ExtendWith(SpringExtension::class))
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisIndexJobDecodeTests(
    @Autowired private val keyValuePersistence: KeyValuePersistenceRedis<UUID>,
    @Autowired private val stringTemplate: ReactiveStringRedisTemplate,
    // The deployed mapper, not a private one. A codec that only ever meets a
    // mapper the test built proves nothing about the mapper the deployment uses.
    @Autowired private val mapper: ObjectMapper,
) {
    private val codec = IndexJobCodec<UUID>(mapper)

    @BeforeEach
    fun `flush redis`() {
        stringTemplate.delete(stringTemplate.keys("*")).block()
    }

    @Test
    fun `a stored job reads back through the codec`() {
        val key = TestKeys.key(UUID.randomUUID())
        val job = IndexJob(
            key = key,
            nodeId = 7,
            keyType = "uuid",
            incarnationId = "incarnation-a",
            startedBy = TestKeys.key(UUID.randomUUID()),
            startedAt = Instant.parse("2026-09-12T12:00:00Z"),
            outcome = JobOutcome.SUCCEEDED,
            indexed = 3L,
        )

        keyValuePersistence.add(KeyValuePair.create(key, job as Any)).block()
        val stored = keyValuePersistence.get(key).block()!!

        Assertions.assertThat(stored.data).isInstanceOf(Map::class.java)

        val decoded = codec.decode(stored.data)

        Assertions.assertThat(decoded.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(decoded.indexed).isEqualTo(3L)
        Assertions.assertThat(decoded.nodeId).isEqualTo(7)
        Assertions.assertThat(decoded.startedAt).isEqualTo(job.startedAt)
        Assertions.assertThat(decoded.covers).isTrue()
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}
