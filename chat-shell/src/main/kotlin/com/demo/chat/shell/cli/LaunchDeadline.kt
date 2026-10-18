package com.demo.chat.shell.cli

import java.io.PrintWriter
import java.lang.management.ManagementFactory
import java.time.Duration
import java.time.Instant

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
 * - [finish] prints the output under a lock. If the limit passed during the
 *   read, it prints nothing and throws. If the output is printed, the watchdog
 *   does not halt the process after it. So standard output is empty on 124.
 *
 * [clock] is a seam for tests.
 */
class LaunchDeadline(
    private val name: String,
    private val launchedAt: Instant,
    private val limit: Duration,
    private val clock: () -> Instant = Instant::now,
) {
    private val lock = Any()
    private var finished = false
    private var expired = false

    val message: String get() = "$name passed its ${limit.toMillis()} ms limit"

    /** The time left. It is zero or negative when the limit passed. */
    fun remaining(): Duration = limit.minus(Duration.between(launchedAt, clock()))

    /** Refuses to start when the budget is spent. */
    fun requireBudget() {
        if (!remaining().isPositive) throw ShellExit(ShellExitStatus.TIMEOUT, message)
    }

    /** Runs [output] only if the limit has not passed, and never while the watchdog halts the process. */
    fun finish(output: () -> Unit) {
        synchronized(lock) {
            if (expired || !remaining().isPositive) throw ShellExit(ShellExitStatus.TIMEOUT, message)
            output()
            finished = true
        }
    }

    /**
     * Halts the process with status 124 at the limit, unless [finish] ran.
     * [halt] is `Runtime.halt` in the process, because a normal exit waits for
     * threads that may be the stalled ones.
     */
    fun startWatchdog(err: PrintWriter, halt: (Int) -> Unit) {
        Thread({
            while (remaining().isPositive) {
                try {
                    Thread.sleep(remaining().toMillis().coerceAtLeast(1))
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
            synchronized(lock) {
                if (finished) return@Thread
                expired = true
                err.println("chat-shell: $message")
                err.flush()
                halt(ShellExitStatus.TIMEOUT.code)
            }
        }, "launch-deadline").apply { isDaemon = true }.start()
    }

    companion object {
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
