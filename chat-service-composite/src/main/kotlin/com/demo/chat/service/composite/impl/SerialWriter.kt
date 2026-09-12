package com.demo.chat.service.composite.impl

import com.demo.chat.domain.ChatException
import reactor.core.Disposable
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs submitted work one piece at a time, and waits for each piece to
 * complete before it starts the next.
 *
 * Every emission result is handled. An ignored result leaves the caller with a
 * Mono that never completes, which is worse than an error, because nothing
 * upstream can react to it.
 */
class SerialWriter(private val emitTimeout: Duration = Duration.ofSeconds(10)) {
    private class Work(val body: Mono<Void>, val result: Sinks.Empty<Void>)

    private val closed = AtomicBoolean(false)
    private val drained = Sinks.empty<Void>()
    private val queue = Sinks.many().unicast().onBackpressureBuffer<Work>()

    /**
     * Every submitted piece of work that has not finished.
     *
     * Shutdown reads this. A piece that never ran must still terminate its
     * caller, because a caller that waits forever is the failure this class
     * exists to prevent.
     */
    private val pending = ConcurrentLinkedQueue<Work>()

    private val worker: Disposable = queue.asFlux()
        .concatMap { work ->
            work.body
                .doOnSuccess {
                    pending.remove(work)
                    work.result.tryEmitEmpty()
                }
                .onErrorResume { error ->
                    pending.remove(work)
                    work.result.tryEmitError(error)
                    Mono.empty()
                }
        }
        .doFinally { drained.tryEmitEmpty() }
        .subscribe()

    fun submit(body: Mono<Void>): Mono<Void> = Mono.defer {
        if (closed.get()) {
            return@defer Mono.error(ChatException("The job writer is closed and accepts no work."))
        }

        val work = Work(body, Sinks.empty())
        pending.add(work)
        try {
            // Two concurrent submissions produce FAIL_NON_SERIALIZED, and
            // busyLooping retries that case. A closed queue produces
            // FAIL_TERMINATED, which throws, so the caller sees an error
            // signal rather than a Mono that never completes.
            queue.emitNext(work, Sinks.EmitFailureHandler.busyLooping(emitTimeout))
        } catch (error: Throwable) {
            pending.remove(work)
            return@defer Mono.error(error)
        }

        work.result.asMono()
    }

    /**
     * Stops new work, waits up to [timeout] for the queued work to finish, and
     * then disposes the worker.
     *
     * **Every caller terminates.** Work that the timeout cuts short receives an
     * error, because disposing the worker leaves its result sink unfinished
     * otherwise, and that caller would wait forever.
     *
     * The wait happens here rather than in the caller. An outer
     * `block(timeout)` would cancel this Mono, and a cancel would dispose the
     * worker with work still queued.
     */
    fun close(timeout: Duration = Duration.ofSeconds(10)): Mono<Void> = Mono.defer {
        closed.set(true)
        try {
            // The same policy the submit path uses. A submit that is emitting
            // at this moment produces FAIL_NON_SERIALIZED here too, and an
            // ignored result would leave the queue open, so drained would
            // never complete and this Mono would never finish.
            queue.emitComplete(Sinks.EmitFailureHandler.busyLooping(emitTimeout))
        } catch (error: Sinks.EmissionException) {
            // FAIL_TERMINATED means the queue is already complete. A second
            // close is not an error.
            if (error.reason != Sinks.EmitResult.FAIL_TERMINATED) {
                return@defer Mono.error(error)
            }
        }

        drained.asMono()
            .timeout(timeout)
            .onErrorResume(TimeoutException::class.java) { Mono.empty<Void>() }
            .then(Mono.fromRunnable<Void> { abandonPending() })
    }

    private fun abandonPending() {
        worker.dispose()

        while (true) {
            val work = pending.poll() ?: break
            work.result.tryEmitError(
                ChatException("The job writer shut down before this work ran.")
            )
        }
    }
}
