package com.demo.chat.test.domain

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.ClaimScheduler
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimStore
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * A store whose answers the test sets.
 *
 * `calls` records every operation in order, so a test can assert the claim
 * order and the release order.
 */
class FakeClaimStore(
    override val backendName: String,
    override val scope: String = "$backendName store for key type long",
    var claimAnswer: () -> ClaimResult = { ClaimResult.Granted },
    var renewAnswer: () -> ClaimResult = { ClaimResult.Granted }
) : NodeIdClaimStore {

    val calls = mutableListOf<String>()

    override fun claim(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        Mono.fromCallable { calls.add("claim:$backendName"); claimAnswer() }

    override fun renew(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        Mono.fromCallable { calls.add("renew:$backendName"); renewAnswer() }

    override fun release(nodeId: NodeId, owner: String): Mono<Void> =
        Mono.fromRunnable { calls.add("release:$backendName") }
}

/**
 * A scheduler the test drives by hand.
 *
 * Nothing runs until the test calls [firePeriodic] or [fireOnce]. The guard
 * timing tests therefore need no real waiting.
 */
class ManualClaimScheduler : ClaimScheduler {

    var periodic: (() -> Unit)? = null
    var once: (() -> Unit)? = null
    var onceDelay: Duration? = null
    val detached = mutableListOf<String>()
    var shutdownCount = 0
    var pretendSchedulerThread = false

    override fun schedulePeriodic(period: Duration, task: () -> Unit): AutoCloseable {
        periodic = task
        return AutoCloseable { periodic = null }
    }

    override fun scheduleOnce(delay: Duration, task: () -> Unit): AutoCloseable {
        once = task
        onceDelay = delay
        return AutoCloseable { once = null; onceDelay = null }
    }

    override fun runDetached(name: String, task: () -> Unit) {
        detached.add(name)
        task()
    }

    override fun shutdownNow() {
        shutdownCount++
    }

    override fun isSchedulerThread(): Boolean = pretendSchedulerThread

    fun firePeriodic() = periodic?.invoke()

    fun fireOnce() = once?.invoke()
}
