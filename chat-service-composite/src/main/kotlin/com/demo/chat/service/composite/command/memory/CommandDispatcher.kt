package com.demo.chat.service.composite.command.memory

import com.demo.chat.domain.command.BackendId
import org.slf4j.LoggerFactory
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.scheduler.Scheduler
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** A test seam before each queue insertion. Production uses [NONE]. */
interface DispatchFaults {
    fun beforeEnqueue(backend: BackendId) {}

    companion object {
        val NONE = object : DispatchFaults {}
    }
}

/**
 * Moves committed entries from the dispatch log to the backend runtimes.
 *
 * The scan keeps staging order. For each room it stops at a hidden entry,
 * and it continues when that entry commits or rolls back. A [signal] starts
 * a scan, and a periodic rescan covers a lost signal. Decision 7 of the spec.
 */
class CommandDispatcher<T : Any, V>(
    private val log: DispatchLog<T, V>,
    private val runtimes: Map<BackendId, BackendRuntime<T, V>>,
    private val scheduler: Scheduler,
    rescanInterval: Duration,
    private val faults: DispatchFaults = DispatchFaults.NONE,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(CommandDispatcher::class.java)
    private val pending = AtomicBoolean(false)
    private val draining = AtomicBoolean(false)
    private val periodic: Disposable = Flux.interval(rescanInterval, rescanInterval, scheduler).subscribe { signal() }

    fun signal() {
        pending.set(true)
        if (draining.compareAndSet(false, true)) scheduler.schedule { drain() }
    }

    private fun drain() {
        try {
            while (pending.getAndSet(false)) scan()
        } finally {
            draining.set(false)
            if (pending.get()) signal()
        }
    }

    private fun scan() {
        val blocked = HashSet<T>()
        val entries = log.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            val room = entry.command.orderingKey
            when (entry.marker.state) {
                CommitMarker.State.ROLLED_BACK -> entries.remove()
                CommitMarker.State.UNCOMMITTED -> blocked.add(room)
                CommitMarker.State.COMMITTED -> if (room !in blocked) {
                    if (dispatchAll(entry)) entries.remove() else blocked.add(room)
                }
            }
        }
    }

    /**
     * Queues each obligation that no queue holds yet. The entry leaves the log
     * only after every obligation is queued. A failure keeps the entry and
     * holds its room, so a rescan queues the rest in order.
     */
    private fun dispatchAll(entry: DispatchEntry<T, V>): Boolean {
        for (backend in entry.command.obligations) {
            if (backend in entry.dispatched) continue
            try {
                faults.beforeEnqueue(backend)
                runtimes.getValue(backend).enqueue(entry.command)
                entry.dispatched.add(backend)
            } catch (e: Exception) {
                logger.warn("Queueing ${entry.command.commandId} for $backend failed. A rescan retries it.", e)
                return false
            }
        }
        return true
    }

    override fun close() = periodic.dispose()
}
