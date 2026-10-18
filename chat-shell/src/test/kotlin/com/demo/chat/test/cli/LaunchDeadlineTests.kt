package com.demo.chat.test.cli

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
}
