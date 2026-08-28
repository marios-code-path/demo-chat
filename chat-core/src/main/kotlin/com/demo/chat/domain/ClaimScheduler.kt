package com.demo.chat.domain

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * The scheduling seam for [NodeIdClaimGuard].
 *
 * The guard owns its schedule. A shared task scheduler can delay a renew
 * behind unrelated work, and a late renew can cost the lease.
 *
 * A test supplies its own implementation and fires tasks by hand, so the
 * timing tests need no real waiting.
 */
interface ClaimScheduler {

    fun schedulePeriodic(period: Duration, task: () -> Unit): AutoCloseable

    fun scheduleOnce(delay: Duration, task: () -> Unit): AutoCloseable

    /**
     * Runs a task off every scheduler thread.
     *
     * The guard closes the context this way. A close started on a scheduler
     * thread would run `destroy` on that same thread, and a `destroy` that
     * waited for the scheduler would wait for itself.
     */
    fun runDetached(name: String, task: () -> Unit)

    fun shutdownNow()

    fun isSchedulerThread(): Boolean
}

class ExecutorClaimScheduler : ClaimScheduler {

    private val threadName = "nodeid-claim-renew"

    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor(ThreadFactory { runnable ->
            Thread(runnable, threadName).apply { isDaemon = true }
        })

    override fun schedulePeriodic(period: Duration, task: () -> Unit): AutoCloseable {
        val future = executor.scheduleAtFixedRate(
            { runQuietly(task) }, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS
        )
        return AutoCloseable { future.cancel(false) }
    }

    override fun scheduleOnce(delay: Duration, task: () -> Unit): AutoCloseable {
        val future = executor.schedule(
            { runQuietly(task) }, delay.toMillis(), TimeUnit.MILLISECONDS
        )
        return AutoCloseable { future.cancel(false) }
    }

    override fun runDetached(name: String, task: () -> Unit) {
        Thread({ runQuietly(task) }, name).apply { isDaemon = false }.start()
    }

    override fun shutdownNow() {
        executor.shutdownNow()
        // Never wait from a scheduler thread. That would wait for this task.
        if (!isSchedulerThread()) {
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    override fun isSchedulerThread(): Boolean = Thread.currentThread().name == threadName

    // A thrown task would cancel a fixed rate schedule without a word. The
    // guard handles its own failures, so anything reaching here is a defect.
    private fun runQuietly(task: () -> Unit) {
        try {
            task()
        } catch (e: Throwable) {
            System.err.println("nodeid claim scheduler task failed: ${e.message}")
        }
    }
}
