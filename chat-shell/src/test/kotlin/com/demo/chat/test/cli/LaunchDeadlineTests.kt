package com.demo.chat.test.cli

import com.demo.chat.shell.cli.HardLimits
import com.demo.chat.shell.cli.LaunchDeadline
import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The hard limit of a command counts from the launch of the process, and not
 * from the start of the command. A startup that stalls, or that already used
 * the whole budget, must not end in a success. Sigma found this on PR #259.
 */
class LaunchDeadlineTests {
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val limit = Duration.ofSeconds(30)

    private fun deadline(launchedAgo: Duration) =
        LaunchDeadline("register-read", now.minus(launchedAgo), limit) { now }

    @Test
    fun `the budget counts from the launch`() {
        assertThat(deadline(Duration.ofSeconds(4)).remaining()).isEqualTo(Duration.ofSeconds(26))
    }

    @Test
    fun `a spent budget is refused before any read`() {
        for (ago in listOf(Duration.ofSeconds(30), Duration.ofSeconds(31), Duration.ofMinutes(5))) {
            assertThatThrownBy { deadline(ago).requireBudget() }
                .describedAs("launched %s ago", ago)
                .isInstanceOfSatisfying(ShellExit::class.java) {
                    assertThat(it.status).isEqualTo(ShellExitStatus.TIMEOUT)
                    assertThat(it.message).isEqualTo("register-read passed its 30000 ms limit")
                }
        }
    }

    @Test
    fun `a budget with time left passes`() {
        deadline(Duration.ofSeconds(29)).requireBudget()
    }

    @Test
    fun `the output is refused, and nothing is printed, when the budget ran out during the read`() {
        var printed = false

        assertThatThrownBy { deadline(Duration.ofSeconds(31)).finish { printed = true } }
            .isInstanceOfSatisfying(ShellExit::class.java) { assertThat(it.status).isEqualTo(ShellExitStatus.TIMEOUT) }
        assertThat(printed).isFalse()
    }

    @Test
    fun `the output prints when the budget holds`() {
        var printed = false

        deadline(Duration.ofSeconds(5)).finish { printed = true }

        assertThat(printed).isTrue()
    }

    @Test
    fun `the launch is the earlier of the launcher stamp and the JVM start`() {
        val jvm = Instant.parse("2026-10-18T11:59:50Z")
        val stamp = jvm.minusMillis(250).toEpochMilli().toString()

        assertThat(LaunchDeadline.launchOfProcess({ if (it == LaunchDeadline.LAUNCH_ENV) stamp else null }, jvm))
            .isEqualTo(jvm.minusMillis(250))
        assertThat(LaunchDeadline.launchOfProcess({ if (it == LaunchDeadline.LAUNCH_ENV) jvm.plusSeconds(9).toEpochMilli().toString() else null }, jvm))
            .describedAs("a stamp after the JVM start does not shorten the time used")
            .isEqualTo(jvm)
    }

    @Test
    fun `a missing or broken stamp falls back to the JVM start, and never to now`() {
        val jvm = Instant.parse("2026-10-18T11:59:50Z")

        assertThat(LaunchDeadline.launchOfProcess({ null }, jvm)).isEqualTo(jvm)
        assertThat(LaunchDeadline.launchOfProcess({ "not a number" }, jvm)).isEqualTo(jvm)
        assertThat(LaunchDeadline.launchOfProcess({ "" }, jvm)).isEqualTo(jvm)
    }

    /** A stall before the command runs: only a timer that began at the launch can stop it. */
    @Test
    fun `the watchdog ends the process with status 124 and one line when the limit passes`() {
        val halted = CountDownLatch(1)
        val status = AtomicInteger()
        val err = StringWriter()
        val launched = Instant.now().minusMillis(1_900)
        val watched = LaunchDeadline("register-read", launched, Duration.ofSeconds(2))

        watched.startWatchdog(PrintWriter(err)) { code -> status.set(code); halted.countDown() }

        assertThat(halted.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(status.get()).isEqualTo(124)
        assertThat(err.toString()).isEqualTo("chat-shell: register-read passed its 2000 ms limit" + System.lineSeparator())
    }

    @Test
    fun `the watchdog fires at once when the budget is spent before it starts`() {
        val halted = CountDownLatch(1)
        val watched = LaunchDeadline("register-read", Instant.now().minusSeconds(40), limit)

        watched.startWatchdog(PrintWriter(StringWriter())) { halted.countDown() }

        assertThat(halted.await(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun `the watchdog does not end a command that has printed its output`() {
        val halts = AtomicInteger()
        val watched = LaunchDeadline("register-read", Instant.now().minusMillis(1_700), Duration.ofSeconds(2))
        watched.startWatchdog(PrintWriter(StringWriter())) { halts.incrementAndGet() }

        watched.finish { }
        Thread.sleep(700)

        assertThat(halts.get()).isZero()
    }

    /**
     * The output can block: a reader that does not read, or a full pipe. The
     * first version held a lock while it printed, so the watchdog waited for
     * that lock and never halted the process. Sigma found this on PR #259.
     */
    @Test
    fun `the watchdog halts the process even when the output is blocked`() {
        val halted = CountDownLatch(1)
        val status = AtomicInteger()
        val neverReleased = CountDownLatch(1)
        val watched = LaunchDeadline("register-read", Instant.now().minusMillis(1_800), Duration.ofSeconds(2))
        watched.startWatchdog(PrintWriter(StringWriter())) { code -> status.set(code); halted.countDown() }

        val printing = Thread({ runCatching { watched.finish { neverReleased.await() } } }, "blocked-output").apply { isDaemon = true }
        printing.start()

        assertThat(halted.await(5, TimeUnit.SECONDS)).describedAs("the process halted").isTrue()
        assertThat(status.get()).isEqualTo(124)
    }

    @Test
    fun `the watchdog halts the process even when standard error is blocked`() {
        val halted = CountDownLatch(1)
        val neverReleased = CountDownLatch(1)
        val blockedErr = object : java.io.Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) { neverReleased.await() }
            override fun flush() {}
            override fun close() {}
        }
        val watched = LaunchDeadline("register-read", Instant.now().minusMillis(1_900), Duration.ofSeconds(2))

        watched.startWatchdog(PrintWriter(blockedErr)) { halted.countDown() }

        assertThat(halted.await(5, TimeUnit.SECONDS)).describedAs("the process halted").isTrue()
    }

    /** `chat-shell-cli` repeats the limit, because its own watchdog covers the time before Java starts. */
    @Test
    fun `the launcher script uses the same limit as the command`() {
        val script = java.nio.file.Files.readString(java.nio.file.Paths.get("../shell-scripts/chat-shell-cli"))

        assertThat(script).contains("register-read) limit_ms=${HardLimits.REGISTER_READ_LIMIT.toMillis()} ;;")
    }

    /**
     * The watchdog marks the command as expired while the output is being written, and
     * the write then returns. The first version ignored the failed transition and
     * reported success, so the process could exit 0 before the halt. Sigma found this
     * on PR #259.
     */
    @Test
    fun `finish does not report success when the watchdog expired during the output`() {
        val halts = AtomicInteger()
        val err = StringWriter()
        val outputStarted = CountDownLatch(1)
        val releaseOutput = CountDownLatch(1)
        val watched = LaunchDeadline("register-read", Instant.now().minusMillis(1_700), Duration.ofSeconds(2))
        watched.startWatchdog(PrintWriter(err)) { halts.incrementAndGet() }
        var returned = false
        var failure: Throwable? = null
        val writer = Thread({
            try {
                watched.finish { outputStarted.countDown(); releaseOutput.await() }
                returned = true
            } catch (e: Throwable) {
                failure = e
            }
        }, "writer").apply { isDaemon = true }
        writer.start()
        assertThat(outputStarted.await(5, TimeUnit.SECONDS)).isTrue()
        val deadline = System.currentTimeMillis() + 5_000
        while (halts.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertThat(halts.get()).describedAs("the watchdog halted").isGreaterThan(0)

        releaseOutput.countDown()
        writer.join(5_000)

        assertThat(returned).describedAs("finish reported success").isFalse()
        assertThat(failure).isInstanceOfSatisfying(ShellExit::class.java) { assertThat(it.status).isEqualTo(ShellExitStatus.TIMEOUT) }
        assertThat(err.toString()).describedAs("one line").isEqualTo("chat-shell: register-read passed its 2000 ms limit" + System.lineSeparator())
    }

    /** With a watchdog, a spent budget ends the process through the same path, so the line is written once. */
    @Test
    fun `a spent budget with a watchdog halts the process once and writes one line`() {
        val halts = AtomicInteger()
        val err = StringWriter()
        val watched = LaunchDeadline("register-read", Instant.now().minusSeconds(31), limit)
        watched.startWatchdog(PrintWriter(err)) { halts.incrementAndGet() }

        assertThatThrownBy { watched.requireBudget() }.isInstanceOf(ShellExit::class.java)
        Thread.sleep(300)

        assertThat(halts.get()).isGreaterThan(0)
        assertThat(err.toString()).isEqualTo("chat-shell: register-read passed its 30000 ms limit" + System.lineSeparator())
    }
}
