package com.demo.chat.shell.cli

import java.io.PrintWriter
import java.lang.management.ManagementFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A hard limit that counts from the launch of the process.
 *
 * Owner decision 20 sets 30 seconds for the session-start read, from the
 * command start to the last output line, with the JVM start included. A timer
 * that begins when the command runs cannot stop a startup that stalls, and a
 * budget that startup already used up must not end in a success. So three
 * parts work together. Sigma found this on PR #259.
 *
 * - [startWatchdog] runs in `main` before the application starts. At the
 *   limit it writes one line on standard error and halts the process with
 *   status 124. A daemon thread does this, so a stall anywhere stops it.
 * - [requireBudget] refuses to start the read when the budget is spent.
 * - [finish] prints the output. If the limit passed during the read, it prints
 *   nothing and throws. If the output is printed, the watchdog does not halt the
 *   process after it. So standard output is empty on 124, except when the limit
 *   passes during a blocked write.
 *
 * [clock] is a seam for tests.
 */
class LaunchDeadline(
    private val name: String,
    private val launchedAt: Instant,
    private val limit: Duration,
    private val clock: () -> Instant = Instant::now,
) {
    /** RUNNING, then PRINTING while the output is written, then FINISHED. EXPIRED once the limit took over. */
    private val state = AtomicInteger(RUNNING)
    private val lineStarted = AtomicBoolean(false)
    private val lineDone = CountDownLatch(1)

    @Volatile
    private var err: PrintWriter? = null

    @Volatile
    private var halt: ((Int) -> Unit)? = null

    val message: String get() = "$name passed its ${limit.toMillis()} ms limit"

    /** The time left. It is zero or negative when the limit passed. */
    fun remaining(): Duration = limit.minus(Duration.between(launchedAt, clock()))

    /** Refuses to start when the budget is spent. */
    fun requireBudget() {
        if (!remaining().isPositive) expire()
    }

    /**
     * Runs [output] only if the limit has not passed and nothing has taken over.
     *
     * **No lock is held while [output] runs.** A reader that does not read can
     * block the write for ever, and a lock would then stop the watchdog from
     * halting the process. If the limit passes during the write, the watchdog
     * halts the process with status 124, and the standard output can hold part
     * of the text. A caller must discard the output of a command that does not
     * exit with 0.
     *
     * **A write that ends after the limit is not a success.** If the watchdog
     * took over while [output] ran, this ends the process through [expire], and
     * it never reports success. Sigma found the first version of this on PR #259.
     */
    fun finish(output: () -> Unit) {
        if (!remaining().isPositive || !state.compareAndSet(RUNNING, PRINTING)) expire()
        output()
        if (!state.compareAndSet(PRINTING, FINISHED)) expire()
    }

    /**
     * The one way that the limit ends a command. With a watchdog it writes the
     * line once, halts the process with status 124, and so never returns. Without
     * a watchdog, in a test or in the interactive shell, it throws a [ShellExit]
     * of status [ShellExitStatus.TIMEOUT]. A caller that sees the limit passed
     * calls this instead of writing its own line, so the line is never doubled.
     */
    fun expire(): Nothing {
        halting()
        throw ShellExit(ShellExitStatus.TIMEOUT, message)
    }

    /**
     * Halts the process with status 124 at the limit, unless [finish] completed.
     * [halt] is `Runtime.halt` in the process, because a normal exit waits for
     * threads that may be the stalled ones.
     *
     * **The halt does not depend on the line.** A blocked standard error would
     * stop the write of the line, so a helper thread writes it, and the halt
     * follows after at most half a second.
     */
    fun startWatchdog(err: PrintWriter, halt: (Int) -> Unit) {
        this.err = err
        this.halt = halt
        Thread({
            while (remaining().isPositive) {
                try {
                    Thread.sleep(remaining().toMillis().coerceAtLeast(1))
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
            halting()
        }, "launch-deadline").apply { isDaemon = true }.start()
    }

    /** Takes over, writes the line once, and halts. It does nothing without a watchdog, or after the output finished. */
    private fun halting() {
        val stop = halt ?: return
        val line = err ?: return
        if (state.getAndSet(EXPIRED) == FINISHED) return
        if (lineStarted.compareAndSet(false, true)) {
            Thread({
                try {
                    line.println("chat-shell: $message")
                    line.flush()
                } finally {
                    lineDone.countDown()
                }
            }, "launch-deadline-line").apply { isDaemon = true }.start()
        }
        lineDone.await(LINE_WAIT_MS, TimeUnit.MILLISECONDS)
        stop(ShellExitStatus.TIMEOUT.code)
    }

    companion object {
        private const val RUNNING = 0
        private const val PRINTING = 1
        private const val FINISHED = 2
        private const val EXPIRED = 3
        private const val LINE_WAIT_MS = 500L

        /** The launcher stamps its own start here, in epoch milliseconds, so its time counts too. */
        const val LAUNCH_ENV = "CHAT_SHELL_LAUNCH_EPOCH_MS"

        /** The deadline that `main` armed, or null. */
        @Volatile
        var active: LaunchDeadline? = null

        /**
         * The launch of this process. It is the earlier of the launcher stamp and
         * the JVM start. A missing or broken stamp falls back to the JVM start,
         * and never to now, so a missing value cannot count as no startup time.
         */
        fun launchOfProcess(
            env: (String) -> String? = System::getenv,
            jvmStart: Instant = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().startTime),
        ): Instant {
            val stamp = env(LAUNCH_ENV)?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
            return if (stamp != null && stamp.isBefore(jvmStart)) stamp else jvmStart
        }

        /** The deadline of this process for [name]: the armed one, or a new one from the launch. */
        fun forProcess(name: String, limit: Duration): LaunchDeadline =
            active?.takeIf { it.name == name } ?: LaunchDeadline(name, launchOfProcess(), limit)
    }
}

/** The commands that have a hard limit from the launch of the process. */
object HardLimits {
    const val REGISTER_READ = "register-read"

    /** Owner decision 20. */
    val REGISTER_READ_LIMIT: Duration = Duration.ofSeconds(30)

    fun of(command: String?): Duration? = if (command == REGISTER_READ) REGISTER_READ_LIMIT else null
}
