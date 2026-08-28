package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.persistence.redis.impl.RedisNodeIdClaimStore
import org.junit.jupiter.api.Assertions
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
import java.time.Duration

/**
 * Store level tests for the redis claim.
 *
 * These call the store directly and pass the TTL as an argument. The guard
 * property rules do not apply here, so a one second lease is legal.
 *
 * Node ids 100 to 109 belong to this class. See the allocation table in the
 * plan. Two contexts against one container must not share a node id.
 */
@Extensions(
    ExtendWith(SpringExtension::class)
)
@Import(RedisPersistenceTestContext::class)
@Tag("integration")
class RedisNodeIdClaimStoreTests(
    @Autowired private val stringTemplate: ReactiveStringRedisTemplate
) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }

    private val longStore by lazy { RedisNodeIdClaimStore(stringTemplate, "long") }
    private val uuidStore by lazy { RedisNodeIdClaimStore(stringTemplate, "uuid") }

    @BeforeEach
    fun `flush redis`() {
        stringTemplate.delete(stringTemplate.keys("chat:nodeclaim:*")).block()
    }

    @Test
    fun `the scope names the backend and the key type`() {
        Assertions.assertEquals("redis", longStore.backendName)
        Assertions.assertEquals("redis store for key type long", longStore.scope)
    }

    @Test
    fun `a second owner is denied and the holder is named`() {
        val node = NodeId(100)
        Assertions.assertEquals(
            ClaimResult.Granted,
            longStore.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        )

        val second = longStore.claim(node, "owner-two", Duration.ofSeconds(30)).block()

        Assertions.assertEquals(ClaimResult.Denied("owner-one"), second)
    }

    @Test
    fun `a release allows a takeover`() {
        val node = NodeId(101)
        longStore.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        longStore.release(node, "owner-one").block()

        Assertions.assertEquals(
            ClaimResult.Granted,
            longStore.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a release by another owner does nothing`() {
        val node = NodeId(102)
        longStore.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        longStore.release(node, "owner-two").block()

        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            longStore.claim(node, "owner-three", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `an expiry allows a takeover`() {
        val node = NodeId(103)
        longStore.claim(node, "owner-one", Duration.ofSeconds(1)).block()
        Thread.sleep(1500)

        Assertions.assertEquals(
            ClaimResult.Granted,
            longStore.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew by the holder extends the lease`() {
        val node = NodeId(104)
        longStore.claim(node, "owner-one", Duration.ofSeconds(1)).block()

        Assertions.assertEquals(
            ClaimResult.Granted,
            longStore.renew(node, "owner-one", Duration.ofSeconds(30)).block()
        )
        Thread.sleep(1500)
        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            longStore.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew by another owner is denied and names the holder`() {
        val node = NodeId(105)
        longStore.claim(node, "owner-one", Duration.ofSeconds(30)).block()

        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            longStore.renew(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew with no live claim reports lost`() {
        Assertions.assertEquals(
            ClaimResult.Lost,
            longStore.renew(NodeId(106), "owner-one", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a long claim and a uuid claim on one node id both hold`() {
        val node = NodeId(107)

        Assertions.assertEquals(
            ClaimResult.Granted,
            longStore.claim(node, "owner-long", Duration.ofSeconds(30)).block()
        )
        Assertions.assertEquals(
            ClaimResult.Granted,
            uuidStore.claim(node, "owner-uuid", Duration.ofSeconds(30)).block()
        )
    }
}
