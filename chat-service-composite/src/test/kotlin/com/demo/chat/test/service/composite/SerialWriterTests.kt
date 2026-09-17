package com.demo.chat.test.service.composite

import com.demo.chat.domain.ChatException
import com.demo.chat.service.composite.impl.SerialWriter
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class SerialWriterTests {

    // Concurrent submission is what produces FAIL_NON_SERIALIZED. Every caller
    // must still receive a completion.
    @Test
    fun `concurrent submissions all complete`() {
        val writer = SerialWriter()
        val done = AtomicInteger()

        StepVerifier
            .create(
                Flux.range(0, 200)
                    .parallel(8)
                    .runOn(Schedulers.boundedElastic())
                    .flatMap { writer.submit(Mono.fromRunnable { done.incrementAndGet() }) }
                    .then()
            )
            .verifyComplete()

        Assertions.assertThat(done.get()).isEqualTo(200)
        writer.close().block()
    }

    // One at a time means one at a time. A second body must not start while
    // the first is still running.
    @Test
    fun `work never overlaps`() {
        val writer = SerialWriter()
        val running = AtomicBoolean(false)
        val overlapped = AtomicBoolean(false)

        // fromRunnable completes empty, and delayElement only delays an onNext.
        // An empty completion passes straight through, so a delay after
        // fromRunnable creates no window at all. fromCallable emits a value,
        // which the delay can hold.
        //
        // The flag clears inside doOnNext, not in doFinally. Reactor runs a
        // doFinally callback after it propagates the terminal signal, so the
        // next body would start before the previous one cleared the flag, and
        // a correct writer would look like it overlapped.
        val body = Mono.fromCallable { !running.compareAndSet(false, true) }
            .delayElement(Duration.ofMillis(5))
            .doOnNext { collided ->
                if (collided) overlapped.set(true)
                running.set(false)
            }
            .then()

        StepVerifier
            .create(Flux.range(0, 50).flatMap { writer.submit(body) }.then())
            .verifyComplete()

        Assertions.assertThat(overlapped.get()).isFalse()
        writer.close().block()
    }

    // close() must let queued work finish. An immediate dispose would strand
    // every queued result, and the caller would wait for a completion that
    // never arrives.
    @Test
    fun `close drains queued work`() {
        val writer = SerialWriter()
        val started = AtomicInteger()
        val done = AtomicInteger()
        val slow = Mono.fromRunnable<Void> { started.incrementAndGet() }
            .then(Mono.delay(Duration.ofMillis(20)))
            .then(Mono.fromRunnable<Void> { done.incrementAndGet() })

        // submit returns a deferred Mono, so nothing reaches the queue until
        // something subscribes. toFuture subscribes now. Closing before this
        // would reject every submission instead of draining it.
        val all = Flux.merge((0 until 10).map { writer.submit(slow) }).then().toFuture()

        Flux.interval(Duration.ZERO, Duration.ofMillis(5))
            .filter { started.get() > 0 }
            .next()
            .block(Duration.ofSeconds(10))

        writer.close().block()
        all.get(10, TimeUnit.SECONDS)

        Assertions.assertThat(done.get()).isEqualTo(10)
    }

    /**
     * The hazard this test exists for.
     *
     * Each body needs 400 milliseconds and the shutdown timeout is 50, so no
     * body can finish. Every caller must receive the shutdown error.
     *
     * The assertion demands that error rather than accepting any termination.
     * A writer that ignored the timeout and drained normally would complete all
     * five callers, and a weaker assertion would pass it.
     */
    @Test
    fun `work that outlasts the shutdown timeout fails every caller`() {
        val writer = SerialWriter()
        val started = AtomicInteger()
        val slow = Mono.fromRunnable<Void> { started.incrementAndGet() }
            .then(Mono.delay(Duration.ofMillis(400)))
            .then()

        val outcomes = (0 until 5).map { writer.submit(slow).materialize().toFuture() }

        Flux.interval(Duration.ZERO, Duration.ofMillis(5))
            .filter { started.get() > 0 }
            .next()
            .block(Duration.ofSeconds(10))

        writer.close(Duration.ofMillis(50)).block()

        outcomes.forEach { outcome ->
            // A stranded caller appears here as a future that never resolves.
            // toFuture() completes with null for an empty Mono, so Reactor 3.8
            // types it nullable. materialize() always emits one signal, so a
            // null here is a defect and the assertion says so.
            val signal = outcome.get(10, TimeUnit.SECONDS)!!

            Assertions.assertThat(signal.hasError())
                .`as`("no body can finish inside the shutdown timeout")
                .isTrue()
            Assertions.assertThat(signal.throwable)
                .isInstanceOf(ChatException::class.java)
                .hasMessageContaining("shut down before this work ran")
        }
    }

    @Test
    fun `a submission after close fails rather than hanging`() {
        val writer = SerialWriter()
        writer.close().block()

        StepVerifier
            .create(writer.submit(Mono.empty()))
            .verifyError(ChatException::class.java)
    }

    @Test
    fun `a failed body reaches its own caller only`() {
        val writer = SerialWriter()

        StepVerifier
            .create(writer.submit(Mono.error(IllegalStateException("write failed"))))
            .verifyError(IllegalStateException::class.java)

        StepVerifier
            .create(writer.submit(Mono.empty()))
            .verifyComplete()

        writer.close().block()
    }
}
