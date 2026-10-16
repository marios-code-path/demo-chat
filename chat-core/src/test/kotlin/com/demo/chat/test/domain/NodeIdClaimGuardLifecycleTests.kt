package com.demo.chat.test.domain

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.LeasePhases
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimGuard
import com.demo.chat.domain.NodeIdClaimProperties
import com.demo.chat.domain.RuntimeOwnerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext

/** The node id guard as a `SmartLifecycle`. See `CHAT-lswjobhz` and `CHAT-ocpojbyy`. */
class NodeIdClaimGuardLifecycleTests {

    private val redis = FakeClaimStore("redis")
    private val scheduler = ManualClaimScheduler()
    private val context = GenericApplicationContext().apply { refresh() }

    private fun guard() = NodeIdClaimGuard(
        listOf(redis), NodeId(7), RuntimeOwnerId.generate("test"),
        NodeIdClaimProperties(null, null, null, null), context, scheduler, clock = { 0L },
    )

    @Test
    fun `stop releases the lease`() {
        val guard = guard()
        guard.start()
        guard.stop()
        assertThat(redis.calls).containsExactly("claim:redis", "release:redis")
        assertThat(guard.isRunning).isFalse()
    }

    @Test
    fun `destroy releases a lease that stop did not release`() {
        val guard = guard()
        guard.start()
        guard.destroy()
        assertThat(redis.calls).containsExactly("claim:redis", "release:redis")
    }

    @Test
    fun `destroy after stop does not release twice`() {
        val guard = guard()
        guard.start()
        guard.stop()
        guard.destroy()
        assertThat(redis.calls.count { it.startsWith("release") }).isEqualTo(1)
    }

    @Test
    fun `the guard is not running before start and after a refused claim`() {
        val guard = guard()
        assertThat(guard.isRunning).isFalse()
        redis.claimAnswer = { ClaimResult.Denied("other") }
        runCatching { guard.start() }
        assertThat(guard.isRunning).isFalse()
        assertThat(scheduler.periodic == null).describedAs("the renew loop is gone after a refused claim").isTrue()
    }

    @Test
    fun `the renew loop runs before the claim returns`() {
        var periodicDuringClaim: Any? = null
        redis.claimAnswer = { periodicDuringClaim = scheduler.periodic; ClaimResult.Granted }
        guard().start()
        assertThat(periodicDuringClaim != null).describedAs("the renew loop during the claim").isTrue()
    }

    @Test
    fun `the guard starts in its own phase`() {
        assertThat(guard().phase).isEqualTo(LeasePhases.NODE_ID_GUARD).isEqualTo(Int.MAX_VALUE - 6144)
    }
}
