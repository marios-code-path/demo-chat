package com.demo.chat.test.domain

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.ExecutorClaimScheduler
import com.demo.chat.domain.HeldLease
import com.demo.chat.domain.LeaseKeeper
import com.demo.chat.domain.NodeIdClaimProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * The renew loop on real threads. See `CHAT-lswjobhz`, plan review items 2
 * and 3.
 *
 * The TTL is 3 s, the renew interval 1 s, the safety margin 1 s and the
 * operation timeout 500 ms. So the close deadline is 2 s.
 */
class LeaseKeeperTimingTests {

    private val props = NodeIdClaimProperties(
        Duration.ofSeconds(3), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(500)
    )
    private val context = GenericApplicationContext().apply { refresh() }
    private val renewals = ExecutorClaimScheduler("test-lease-renew")
    private val deadlines = ExecutorClaimScheduler("test-lease-deadline")
    private val names = (1..12).map { "lease-%02d".format(it) }

    @AfterEach
    fun shutdown() {
        renewals.shutdownNow()
        deadlines.shutdownNow()
    }

    /** Leases with a TTL on the real clock. The store applies a request when its delay ends. */
    private class ExpiringLeases(private val ttl: Duration) {
        val expiresAt = ConcurrentHashMap<String, Long>()
        val renewStarts = ConcurrentHashMap<String, Long>()

        @Volatile var claimDelay: Duration = Duration.ZERO
        @Volatile var renewDelay: Duration = Duration.ZERO
        @Volatile var renewBlocks: Duration = Duration.ZERO
        @Volatile var renewFails = false

        fun claimOf(name: String): Mono<ClaimResult> = Mono.delay(claimDelay).map {
            expiresAt[name] = System.nanoTime() + ttl.toNanos()
            ClaimResult.Granted
        }

        fun renewOf(name: String): Mono<ClaimResult> = Mono.defer {
            renewStarts.putIfAbsent(name, System.nanoTime())
            // A blocking store call holds the renew thread here.
            if (!renewBlocks.isZero) Thread.sleep(renewBlocks.toMillis())
            Mono.delay(renewDelay).map {
                check(!renewFails) { "the store is down" }
                val at = expiresAt[name]
                if (at == null || at < System.nanoTime()) ClaimResult.Lost
                else {
                    expiresAt[name] = System.nanoTime() + ttl.toNanos()
                    ClaimResult.Granted
                }
            }
        }

        fun releaseOf(name: String): Mono<Void> = Mono.fromRunnable { expiresAt.remove(name) }

        fun live(name: String): Boolean = (expiresAt[name] ?: 0L) > System.nanoTime()

        fun lease(name: String): HeldLease = object : HeldLease {
            override val label = name
            override fun renew() = renewOf(name)
            override fun release() = releaseOf(name)
            override fun deniedMessage(holder: String) = "$name is held by $holder"
            override fun lostMessage() = "$name is lost"
        }
    }

    private fun keeper() = LeaseKeeper("test leases", props, context, renewals, deadlines, "test-lease-close")

    private fun claimAll(keeper: LeaseKeeper, store: ExpiringLeases) {
        keeper.begin()
        names.forEach { name ->
            val sentAt = keeper.now()
            store.claimOf(name).block()
            keeper.add(store.lease(name), sentAt)
        }
        keeper.held()
    }

    private fun waitUntil(limit: Duration, condition: () -> Boolean) {
        val end = System.nanoTime() + limit.toNanos()
        while (!condition() && System.nanoTime() < end) Thread.sleep(20)
    }

    @Test
    fun `slow claims keep every early lease alive`() {
        val store = ExpiringLeases(props.ttl).apply { claimDelay = Duration.ofMillis(400) }
        val keeper = keeper()
        val started = System.nanoTime()

        claimAll(keeper, store)

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThan(props.ttl)
        assertThat(names.filterNot(store::live)).describedAs("leases that expired during the claims").isEmpty()
        keeper.releaseAll()
    }

    @Test
    fun `a claim batch that passes a deadline fails and releases every lease`() {
        val store = ExpiringLeases(props.ttl).apply {
            claimDelay = Duration.ofMillis(400)
            renewFails = true
        }
        val keeper = keeper()

        assertThatThrownBy {
            try {
                claimAll(keeper, store)
            } catch (failure: Throwable) {
                keeper.releaseAll()
                throw failure
            }
        }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("before the claims ended")

        assertThat(store.expiresAt).isEmpty()
        assertThat(context.isActive).describedAs("a claim failure fails the start, not the context").isTrue()
    }

    @Test
    fun `the deadline closes the context while every renewal blocks its thread`() {
        val store = ExpiringLeases(props.ttl)
        val keeper = keeper()
        claimAll(keeper, store)
        val heldAt = System.nanoTime()
        store.renewBlocks = Duration.ofSeconds(10)

        waitUntil(Duration.ofSeconds(6)) { !context.isActive }

        assertThat(context.isActive).describedAs("the context after the deadline").isFalse()
        assertThat(Duration.ofNanos(System.nanoTime() - heldAt)).isLessThan(props.closeDeadline.plusMillis(1500))
    }

    @Test
    fun `one renewal round sends every renewal at once`() {
        val store = ExpiringLeases(props.ttl).apply { renewDelay = Duration.ofMillis(300) }
        val keeper = keeper()
        claimAll(keeper, store)
        store.renewStarts.clear()

        waitUntil(Duration.ofSeconds(3)) { store.renewStarts.size == names.size }

        assertThat(store.renewStarts).hasSize(names.size)
        val spread = store.renewStarts.values.max() - store.renewStarts.values.min()
        assertThat(Duration.ofNanos(spread)).isLessThan(Duration.ofMillis(200))
        assertThat(context.isActive).isTrue()
        keeper.releaseAll()
    }
}
