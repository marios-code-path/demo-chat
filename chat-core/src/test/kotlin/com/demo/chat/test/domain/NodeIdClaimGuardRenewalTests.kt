package com.demo.chat.test.domain

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimGuard
import com.demo.chat.domain.NodeIdClaimProperties
import com.demo.chat.domain.RuntimeOwnerId
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import java.time.Duration

class NodeIdClaimGuardRenewalTests {

    private val props = NodeIdClaimProperties(null, null, null, null)

    private fun setUp(store: FakeClaimStore): Pair<NodeIdClaimGuard, ManualClaimScheduler> {
        val scheduler = ManualClaimScheduler()
        val context = GenericApplicationContext()
        context.refresh()
        val guard = NodeIdClaimGuard(
            listOf(store), NodeId(7), RuntimeOwnerId("core-service@host-a:4711#a3f19c2b"),
            props, context, scheduler
        )
        guard.afterPropertiesSet()
        return guard to scheduler
    }

    @Test
    fun `a granted renew keeps the context open and rearms the deadline`() {
        val store = FakeClaimStore("redis")
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()

        Assertions.assertTrue(store.calls.contains("renew:redis"))
        Assertions.assertTrue(scheduler.detached.isEmpty())
        Assertions.assertEquals(Duration.ofSeconds(25), scheduler.onceDelay)
    }

    @Test
    fun `a denied renew closes the context at once`() {
        val store = FakeClaimStore("redis", renewAnswer = { ClaimResult.Denied("other-owner") })
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()

        Assertions.assertEquals(listOf("nodeid-claim-close"), scheduler.detached)
    }

    @Test
    fun `a lost renew closes the context at once`() {
        val store = FakeClaimStore("redis", renewAnswer = { ClaimResult.Lost })
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()

        Assertions.assertEquals(listOf("nodeid-claim-close"), scheduler.detached)
    }

    @Test
    fun `a renew error keeps the context open`() {
        val store = FakeClaimStore("redis", renewAnswer = { throw IllegalStateException("redis is down") })
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()
        scheduler.firePeriodic()

        Assertions.assertTrue(scheduler.detached.isEmpty())
    }

    @Test
    fun `a renew error does not rearm the deadline`() {
        val store = FakeClaimStore("redis")
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()
        val armedAfterSuccess = scheduler.once
        store.renewAnswer = { throw IllegalStateException("redis is down") }
        scheduler.firePeriodic()

        Assertions.assertSame(armedAfterSuccess, scheduler.once)
    }

    @Test
    fun `the deadline timer closes the context when it fires`() {
        val store = FakeClaimStore("redis", renewAnswer = { throw IllegalStateException("redis is down") })
        val (_, scheduler) = setUp(store)

        scheduler.firePeriodic()
        scheduler.fireOnce()

        Assertions.assertEquals(listOf("nodeid-claim-close"), scheduler.detached)
    }

    @Test
    fun `the close runs off the scheduler thread`() {
        val store = FakeClaimStore("redis", renewAnswer = { ClaimResult.Lost })
        val (_, scheduler) = setUp(store)
        scheduler.pretendSchedulerThread = true

        scheduler.firePeriodic()

        Assertions.assertEquals(listOf("nodeid-claim-close"), scheduler.detached)
    }

    @Test
    fun `destroy shuts the scheduler down and releases the store`() {
        val store = FakeClaimStore("redis")
        val (guard, scheduler) = setUp(store)

        guard.destroy()

        Assertions.assertEquals(1, scheduler.shutdownCount)
        Assertions.assertTrue(store.calls.contains("release:redis"))
    }

    @Test
    fun `destroy releases two stores in reverse backend name order`() {
        val redis = FakeClaimStore("redis")
        val cassandra = FakeClaimStore("cassandra")
        val scheduler = ManualClaimScheduler()
        val context = GenericApplicationContext()
        context.refresh()
        val guard = NodeIdClaimGuard(
            listOf(redis, cassandra), NodeId(7),
            RuntimeOwnerId("core-service@host-a:4711#a3f19c2b"), props, context, scheduler
        )
        guard.afterPropertiesSet()

        guard.destroy()

        Assertions.assertTrue(redis.calls.contains("release:redis"))
        Assertions.assertTrue(cassandra.calls.contains("release:cassandra"))
    }

    @Test
    fun `destroy twice releases once`() {
        val store = FakeClaimStore("redis")
        val (guard, _) = setUp(store)

        guard.destroy()
        guard.destroy()

        Assertions.assertEquals(1, store.calls.count { it == "release:redis" })
    }
}
