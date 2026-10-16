package com.demo.chat.domain

import org.slf4j.LoggerFactory
import org.springframework.context.ConfigurableApplicationContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The lifecycle phases of the lease guards. See `CHAT-lswjobhz`.
 *
 * Spring starts phases in ascending order and stops them in descending order.
 * The connection factories start at phase 0, and `RootKeyStartup` at
 * `Int.MAX_VALUE - 4096`. So a guard claims after its backend starts and
 * before any start scan. It releases after the servers and the writers stop.
 */
object LeasePhases {
    const val NODE_ID_GUARD = Int.MAX_VALUE - 6144
    const val SERVICE_GUARD = Int.MAX_VALUE - 5120

    /** A background writer stops here, before the service guard releases. */
    const val WRITERS = Int.MAX_VALUE - 4608
}

/** One lease that a guard holds and renews. The keeper applies the operation timeout. */
interface HeldLease {
    /** Names the lease in a log line. */
    val label: String

    /** Extends the lease. An error is retried at the next interval. */
    fun renew(): Mono<ClaimResult>

    /** Drops the lease when this owner holds it. */
    fun release(): Mono<Void>

    fun deniedMessage(holder: String): String

    fun lostMessage(): String
}

/**
 * Renews the leases of one guard, and closes the context when it cannot.
 * `NodeIdClaimGuard` and `ServiceLeaseGuard` share it.
 *
 * The renew loop starts before the first claim, so a slow claim batch cannot
 * let an early lease expire. One round sends every renewal at once.
 *
 * The deadline check runs on [deadlines], and it never blocks. A renewal that
 * blocks the [renewals] thread therefore cannot delay it. The check closes
 * the context when a lease has no granted request within
 * `ttl - safety-margin`, measured from the send time of the request. A
 * process pause longer than the safety margin defeats this. See the guarantee
 * in `docs/superpowers/specs/2026-10-09-store-service-lease-design.md`.
 *
 * A keeper runs once. A guard that stops cannot start again.
 */
class LeaseKeeper(
    private val subject: String,
    private val props: NodeIdClaimProperties,
    private val context: ConfigurableApplicationContext,
    private val renewals: ClaimScheduler,
    private val deadlines: ClaimScheduler,
    private val closeThreadName: String,
    private val clock: () -> Long = System::nanoTime,
) {
    private enum class State { IDLE, CLAIMING, HOLDING, ENDED }

    /** One lease, and the send time of its last granted claim or renewal. */
    private class Entry(val lease: HeldLease, @Volatile var confirmedAt: Long)

    private data class Outcome(val entry: Entry, val result: ClaimResult, val sentAt: Long)

    private val log = LoggerFactory.getLogger(LeaseKeeper::class.java)
    private val entries = CopyOnWriteArrayList<Entry>()
    private val closing = AtomicBoolean(false)
    private val deadlineNanos = props.closeDeadline.toNanos()

    @Volatile
    private var state = State.IDLE

    @Volatile
    private var claimFailure: String? = null
    private var renewHandle: AutoCloseable? = null
    private var watchHandle: AutoCloseable? = null

    /** True while the keeper holds at least one lease. */
    val holding: Boolean get() = state != State.ENDED && entries.isNotEmpty()

    fun now(): Long = clock()

    /** Starts the renew loop. Call it before the first claim. */
    @Synchronized
    fun begin() {
        check(state == State.IDLE) { "$subject: a lease keeper runs once." }
        state = State.CLAIMING
        renewHandle = renewals.schedulePeriodic(props.renewInterval) { renewOnce() }
    }

    /** Adds a granted lease. [sentAt] is the clock reading taken before the claim was sent. */
    fun add(lease: HeldLease, sentAt: Long) {
        entries.add(Entry(lease, sentAt))
        checkClaimPhase()
    }

    /** Ends the claim batch. From now on, a missed deadline closes the context. */
    @Synchronized
    fun held() {
        checkClaimPhase()
        state = State.HOLDING
        armWatch()
    }

    /** Releases every lease, in reverse order of subscription. A second call releases nothing. */
    fun releaseAll() {
        val leases = end()
        if (leases.isEmpty()) return
        try {
            Flux.fromIterable(leases.reversed())
                .flatMap({ lease ->
                    Mono.defer { lease.release() }
                        .timeout(props.operationTimeout)
                        .onErrorResume { error ->
                            log.warn("{} release failed. The lease expires on its own. Cause: {}", lease.label, error.message)
                            Mono.empty()
                        }
                }, leases.size)
                .then()
                .block(props.operationTimeout.plusSeconds(1))
        } catch (error: RuntimeException) {
            log.warn("{}: the release did not end in time. The leases expire on their own. Cause: {}", subject, error.message)
        }
    }

    /** Stops the renewals and keeps the leases. They expire after `ttl`. */
    fun abandon() {
        val leases = end()
        if (leases.isNotEmpty()) {
            log.error("{}: {} leases are kept, not released. They expire within {}s.", subject, leases.size, props.ttl.seconds)
        }
    }

    @Synchronized
    private fun end(): List<HeldLease> {
        state = State.ENDED
        renewHandle?.close()
        renewHandle = null
        watchHandle?.close()
        watchHandle = null
        val leases = entries.map { it.lease }
        entries.clear()
        return leases
    }

    private fun checkClaimPhase() {
        claimFailure?.let { throw IllegalStateException(it) }
        val now = clock()
        entries.firstOrNull { now - it.confirmedAt >= deadlineNanos }?.let { late ->
            throw IllegalStateException(
                "${late.lease.label} passed its close deadline of ${props.closeDeadline.seconds}s " +
                    "before the claims ended. The start fails, and every lease is released."
            )
        }
    }

    private fun renewOnce() {
        val snapshot = entries.toList()
        if (snapshot.isEmpty() || state == State.ENDED) return
        val outcomes = (try {
            Flux.fromIterable(snapshot)
                .flatMap({ entry ->
                    Mono.defer {
                        val sentAt = clock()
                        entry.lease.renew().timeout(props.operationTimeout).map { Outcome(entry, it, sentAt) }
                    }.onErrorResume { error ->
                        log.warn("{} renew failed. Retrying at the next interval. Cause: {}", entry.lease.label, error.message)
                        Mono.empty()
                    }
                }, snapshot.size)
                .collectList()
                .block(props.operationTimeout.plusSeconds(1))
        } catch (error: RuntimeException) {
            log.warn("{}: a renewal round did not end in time. Cause: {}", subject, error.message)
            null
        }) ?: return
        if (state == State.ENDED) return
        outcomes.forEach { (entry, result, sentAt) ->
            when (result) {
                is ClaimResult.Granted -> entry.confirmedAt = maxOf(entry.confirmedAt, sentAt)
                is ClaimResult.Denied -> fail(entry.lease.deniedMessage(result.holder))
                is ClaimResult.Lost -> fail(entry.lease.lostMessage())
            }
        }
    }

    // A refusal during the claim batch fails the start. The context is still
    // in its refresh, so the keeper does not close it.
    private fun fail(reason: String) {
        if (state == State.CLAIMING) {
            if (claimFailure == null) claimFailure = reason
        } else {
            closeContext(reason)
        }
    }

    @Synchronized
    private fun armWatch() {
        if (state != State.HOLDING) return
        val oldest = entries.minOfOrNull { it.confirmedAt } ?: return
        val delay = Duration.ofNanos(maxOf(0L, oldest + deadlineNanos - clock()))
        watchHandle = deadlines.scheduleOnce(delay) { watch() }
    }

    // Runs on the deadline thread. It reads volatile fields and never blocks.
    private fun watch() {
        if (state != State.HOLDING) return
        val now = clock()
        val late = entries.firstOrNull { now - it.confirmedAt >= deadlineNanos }
        if (late == null) {
            armWatch()
        } else {
            closeContext(
                "${late.lease.label} was not renewed within ${props.closeDeadline.seconds}s. " +
                    "The lease may have expired. Closing the application context."
            )
        }
    }

    // The close runs off both schedulers. A close started on a scheduler thread
    // would run destroy on that same thread, and destroy would wait for it.
    private fun closeContext(reason: String) {
        if (!closing.compareAndSet(false, true)) return
        log.error(reason)
        deadlines.runDetached(closeThreadName) { context.close() }
    }
}
