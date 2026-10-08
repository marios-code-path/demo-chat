package com.demo.chat.service.composite.command.publication

import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs one task at a time. A task submitted during another task waits until
 * that task completes, so no task reenters another. Decision 9 of the spec.
 */
class SerialTaskQueue(private val scheduler: Scheduler) {
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    private val running = AtomicBoolean(false)

    fun <R : Any> submit(task: () -> Mono<R>): Mono<R> = Mono.create { sink ->
        tasks.add(Runnable {
            Mono.defer(task)
                .doFinally { release() }
                .subscribe({ sink.success(it) }, { sink.error(it) }, { sink.success() })
        })
        tryStart()
    }

    private fun tryStart() {
        if (tasks.isEmpty() || !running.compareAndSet(false, true)) return
        val next = tasks.poll()
        if (next == null) {
            running.set(false)
            tryStart()
            return
        }
        scheduler.schedule(next)
    }

    private fun release() {
        running.set(false)
        tryStart()
    }
}
