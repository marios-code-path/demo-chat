package com.demo.chat.test.domain

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimException
import com.demo.chat.domain.NodeIdClaimGuard
import com.demo.chat.domain.NodeIdClaimProperties
import com.demo.chat.domain.NodeIdClaimStore
import com.demo.chat.domain.RuntimeOwnerId
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import reactor.core.publisher.Mono
import java.time.Duration

class NodeIdClaimGuardStartupTests {

    private val props = NodeIdClaimProperties(null, null, null, null)

    private fun guard(
        stores: List<NodeIdClaimStore>,
        scheduler: ManualClaimScheduler = ManualClaimScheduler()
    ) = NodeIdClaimGuard(
        stores, NodeId(7), RuntimeOwnerId("core-service@host-a:4711#a3f19c2b"),
        props, GenericApplicationContext(), scheduler
    )

    @Test
    fun `one granted store starts and schedules a renew`() {
        val store = FakeClaimStore("redis")
        val scheduler = ManualClaimScheduler()
        guard(listOf(store), scheduler).afterPropertiesSet()

        Assertions.assertEquals(listOf("claim:redis"), store.calls)
        Assertions.assertNotNull(scheduler.periodic)
    }

    @Test
    fun `two stores are claimed in backend name order`() {
        val redis = FakeClaimStore("redis")
        val cassandra = FakeClaimStore("cassandra")
        guard(listOf(redis, cassandra)).afterPropertiesSet()

        Assertions.assertEquals(listOf("claim:cassandra"), cassandra.calls)
        Assertions.assertEquals(listOf("claim:redis"), redis.calls)
    }

    @Test
    fun `a denial at the second store releases the first store`() {
        val cassandra = FakeClaimStore("cassandra")
        val redis = FakeClaimStore(
            "redis",
            claimAnswer = { ClaimResult.Denied("core-service@host-b:5122#77c0aa41") }
        )

        val thrown = Assertions.assertThrows(NodeIdClaimException::class.java) {
            guard(listOf(redis, cassandra)).afterPropertiesSet()
        }

        Assertions.assertEquals(listOf("claim:cassandra", "release:cassandra"), cassandra.calls)
        Assertions.assertTrue(thrown.message!!.contains("app.nodeid=7 is already claimed"))
        Assertions.assertTrue(thrown.message!!.contains("redis store for key type long"))
    }

    @Test
    fun `a store error at the second store releases the first store`() {
        val cassandra = FakeClaimStore("cassandra")
        val redis = FakeClaimStore("redis", claimAnswer = { throw IllegalStateException("redis is down") })

        Assertions.assertThrows(Exception::class.java) {
            guard(listOf(redis, cassandra)).afterPropertiesSet()
        }

        Assertions.assertEquals(listOf("claim:cassandra", "release:cassandra"), cassandra.calls)
    }

    @Test
    fun `a release failure during rollback does not hide the claim failure`() {
        val cassandra = object : NodeIdClaimStore {
            override val backendName = "cassandra"
            override val scope = "cassandra keyspace chat_long"
            override fun claim(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
                Mono.just(ClaimResult.Granted)
            override fun renew(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
                Mono.just(ClaimResult.Granted)
            override fun release(nodeId: NodeId, owner: String): Mono<Void> =
                Mono.error(IllegalStateException("release failed"))
        }
        val redis = FakeClaimStore("redis", claimAnswer = { ClaimResult.Denied("other") })

        val thrown = Assertions.assertThrows(NodeIdClaimException::class.java) {
            guard(listOf(redis, cassandra)).afterPropertiesSet()
        }
        Assertions.assertTrue(thrown.message!!.contains("app.nodeid=7"))
    }

    @Test
    fun `the deadline timer is armed after a successful claim`() {
        val scheduler = ManualClaimScheduler()
        guard(listOf(FakeClaimStore("redis")), scheduler).afterPropertiesSet()

        Assertions.assertNotNull(scheduler.once)
        Assertions.assertEquals(Duration.ofSeconds(25), scheduler.onceDelay)
    }

    @Test
    fun `no stores means no claim and no schedule`() {
        val scheduler = ManualClaimScheduler()
        guard(emptyList(), scheduler).afterPropertiesSet()

        Assertions.assertNull(scheduler.periodic)
        Assertions.assertNull(scheduler.once)
    }
}
