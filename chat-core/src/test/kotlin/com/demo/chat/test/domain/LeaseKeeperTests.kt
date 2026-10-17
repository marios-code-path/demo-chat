package com.demo.chat.test.domain

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.HeldLease
import com.demo.chat.domain.LeaseKeeper
import com.demo.chat.domain.NodeIdClaimProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * The renew loop and the close deadline. See `CHAT-lswjobhz`, plan review
 * items 2 and 3.
 *
 * These tests use a fake clock and two manual schedulers, one for the
 * renewals and one for the deadline. No assertion depends on real time, so a
 * slow or busy machine cannot change a result.
 *
 * The TTL is 3 s, the renew interval 1 s, the safety margin 1 s and the
 * operation timeout 500 ms. So the close deadline is 2 s.
 */
class LeaseKeeperTests {

    private val props = NodeIdClaimProperties(
        Duration.ofSeconds(3), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(500)
    )
    private val context = GenericApplicationContext().apply { refresh() }
    private val renewals = ManualClaimScheduler()
    private val deadlines = ManualClaimScheduler()
    private var now = 0L
    private val names = (1..12).map { "lease-%02d".format(it) }

    private fun keeper() = LeaseKeeper("test leases", props, context, renewals, deadlines, "test-lease-close") { now }

    private fun at(millis: Long) {
        now = Duration.ofMillis(millis).toNanos()
    }

    /** Leases whose renewal [answer] the test sets. `renews` counts the renewals of each lease. */
    private class Leases {
        val renews = mutableMapOf<String, Int>()
        val released = mutableListOf<String>()
        var answer: (String) -> Mono<ClaimResult> = { Mono.just(ClaimResult.Granted) }

        fun lease(name: String): HeldLease = object : HeldLease {
            override val label = name
            override fun renew(): Mono<ClaimResult> = Mono.defer {
                renews.merge(name, 1, Int::plus)
                answer(name)
            }
            override fun release(): Mono<Void> = Mono.fromRunnable { released.add(name) }
            override fun deniedMessage(holder: String) = "$name is held by $holder"
            override fun lostMessage() = "$name is lost"
        }
    }

    /** Claims the twelve names, 400 ms apart, and fires the renew loop on each 1 s boundary. */
    private fun slowClaims(keeper: LeaseKeeper, leases: Leases) {
        keeper.begin()
        names.forEachIndexed { i, name ->
            val sentAt = now
            at((i + 1) * 400L)
            keeper.add(leases.lease(name), sentAt)
            if (i * 400L / 1000 != (i + 1) * 400L / 1000) renewals.firePeriodic()
        }
        keeper.held()
    }

    @Test
    fun `slow claims keep every early lease alive`() {
        val leases = Leases()
        val keeper = keeper()

        slowClaims(keeper, leases)

        assertThat(now).isGreaterThan(props.ttl.toNanos())
        assertThat(leases.renews["lease-01"]).describedAs("the renewals of the first lease during the claims").isEqualTo(4)
        // The first lease was renewed at 4.0 s, so its deadline is 6.0 s.
        at(5_000)
        deadlines.fireOnce()
        assertThat(context.isActive).isTrue()
    }

    @Test
    fun `a claim batch that passes a deadline fails and releases every lease`() {
        val leases = Leases().apply { answer = { Mono.error(IllegalStateException("the store is down")) } }
        val keeper = keeper()

        assertThatThrownBy {
            try {
                slowClaims(keeper, leases)
            } catch (failure: Throwable) {
                keeper.releaseAll()
                throw failure
            }
        }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("before the claims ended")

        // The first lease passes its 2 s deadline at the claim that ends at 2.0 s.
        assertThat(leases.released).containsExactlyInAnyOrder("lease-01", "lease-02", "lease-03", "lease-04", "lease-05")
        assertThat(context.isActive).describedAs("a claim failure fails the start, not the context").isTrue()
    }

    @Test
    fun `the deadline runs on its own scheduler, so a blocked renew thread cannot delay it`() {
        val keeper = keeper()
        keeper.begin()
        keeper.add(Leases().lease("lease-01"), now)
        keeper.held()

        assertThat(renewals.once == null).describedAs("the renew scheduler holds no deadline").isTrue()
        assertThat(deadlines.onceDelay).isEqualTo(props.closeDeadline)

        // The renew loop never fires, as on a blocked renew thread. The
        // deadline fires on its own scheduler and closes the context.
        at(2_000)
        deadlines.fireOnce()

        assertThat(deadlines.detached).containsExactly("test-lease-close")
        assertThat(context.isActive).isFalse()
    }

    @Test
    fun `one renewal round sends every renewal at once`() {
        // Each renewal waits until all twelve are sent. A round that sends
        // one renewal at a time would wait out the operation timeout on each.
        val sent = AtomicInteger()
        val allSent = Sinks.empty<Void>()
        val leases = Leases().apply {
            answer = {
                if (sent.incrementAndGet() == names.size) allSent.tryEmitEmpty()
                allSent.asMono().thenReturn(ClaimResult.Granted)
            }
        }
        val keeper = keeper()
        keeper.begin()
        names.forEach { keeper.add(leases.lease(it), now) }
        keeper.held()

        at(1_000)
        renewals.firePeriodic()

        assertThat(sent.get()).isEqualTo(names.size)
        // Every lease was renewed at 1.0 s, so no deadline passes at 2.5 s.
        at(2_500)
        deadlines.fireOnce()
        assertThat(context.isActive).isTrue()
        assertThat(deadlines.onceDelay).isEqualTo(Duration.ofMillis(500))
    }
}
