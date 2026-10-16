package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.ClaimResult
import com.demo.chat.persistence.redis.impl.RedisServiceLeaseStore
import org.assertj.core.api.Assertions.assertThat
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
import reactor.test.StepVerifier
import java.time.Duration

/**
 * Store level tests for the Redis store service lease. See `CHAT-lswjobhz`.
 *
 * These call the store directly and pass the TTL as an argument, so a one
 * second lease is legal. Each test uses its own lease names.
 */
@Extensions(
    ExtendWith(SpringExtension::class)
)
@Import(RedisPersistenceTestContext::class)
@Tag("integration")
class RedisServiceLeaseStoreTests(
    @Autowired private val stringTemplate: ReactiveStringRedisTemplate
) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }

    @BeforeEach
    fun `flush the leases`() {
        stringTemplate.delete(stringTemplate.keys("chat:lease:*")).block()
    }

    private fun store(keyType: String = "long") = RedisServiceLeaseStore(stringTemplate, keyType)
    private val long = Duration.ofSeconds(30)

    @Test
    fun `a claim is granted, and a second owner is denied with the holder named`() {
        StepVerifier.create(store().claim("index:contract-a", "owner-1", long)).expectNext(ClaimResult.Granted).verifyComplete()
        StepVerifier.create(store().claim("index:contract-a", "owner-2", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
    }

    @Test
    fun `the owner renews, and another owner is denied`() {
        store().claim("index:contract-b", "owner-1", long).block()
        StepVerifier.create(store().renew("index:contract-b", "owner-1", long)).expectNext(ClaimResult.Granted).verifyComplete()
        StepVerifier.create(store().renew("index:contract-b", "owner-2", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
    }

    @Test
    fun `a release by another owner keeps the lease`() {
        store().claim("index:contract-c", "owner-1", long).block()
        store().release("index:contract-c", "owner-2").block()
        StepVerifier.create(store().claim("index:contract-c", "owner-3", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
        store().release("index:contract-c", "owner-1").block()
        StepVerifier.create(store().claim("index:contract-c", "owner-3", long)).expectNext(ClaimResult.Granted).verifyComplete()
    }

    @Test
    fun `an expired lease reports lost to its owner and admits a new owner`() {
        store().claim("index:contract-d", "owner-1", Duration.ofSeconds(1)).block()
        Thread.sleep(1500)
        StepVerifier.create(store().renew("index:contract-d", "owner-1", long)).expectNext(ClaimResult.Lost).verifyComplete()
        StepVerifier.create(store().claim("index:contract-d", "owner-2", long)).expectNext(ClaimResult.Granted).verifyComplete()
    }

    @Test
    fun `two key types hold separate leases on one Redis`() {
        StepVerifier.create(store("long").claim("index:contract-e", "owner-1", long)).expectNext(ClaimResult.Granted).verifyComplete()
        StepVerifier.create(store("uuid").claim("index:contract-e", "owner-2", long)).expectNext(ClaimResult.Granted).verifyComplete()
    }

    @Test
    fun `the lease key carries the key type and the name`() {
        store().claim("index:contract-f", "owner-1", long).block()
        assertThat(stringTemplate.hasKey("chat:lease:long:index:contract-f").block()).isTrue()
    }
}
