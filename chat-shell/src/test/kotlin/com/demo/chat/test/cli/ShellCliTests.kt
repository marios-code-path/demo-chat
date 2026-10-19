package com.demo.chat.test.cli

import com.demo.chat.reportFailure
import com.demo.chat.statusOfRun
import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import java.io.PrintWriter
import java.io.StringWriter
import java.net.ConnectException

/**
 * The entry point turns a failure into one process exit status. A [ShellExit]
 * already has its line on standard error, so the entry point adds none. Any
 * other failure gets one line. See CHAT-jmnjvupr and the design record F2.
 */
class ShellCliTests {
    private fun report(error: Throwable): Pair<ShellExitStatus, String> {
        val err = StringWriter()
        val status = reportFailure(error, PrintWriter(err))
        return status to err.toString()
    }

    @Test
    fun `a ShellExit anywhere in the causes keeps its status and adds no line`() {
        // Spring Boot wraps a runner failure in an IllegalStateException.
        val wrapped = IllegalStateException("Failed to execute ApplicationRunner", ShellExit(ShellExitStatus.USAGE, "bad line"))

        assertThat(report(wrapped)).isEqualTo(ShellExitStatus.USAGE to "")
    }

    @Test
    fun `a core that is not available at start gives UNAVAILABLE and one line`() {
        val (status, text) = report(IllegalStateException("start failed", ConnectException("Connection refused")))

        assertThat(status).isEqualTo(ShellExitStatus.UNAVAILABLE)
        assertThat(text).isEqualTo("chat-shell: Connection refused" + System.lineSeparator())
    }

    @Test
    fun `any other failure gives FAILED and one line with the first message line`() {
        val (status, text) = report(IllegalStateException("context broke\nsecond line"))

        assertThat(status).isEqualTo(ShellExitStatus.FAILED)
        assertThat(text).isEqualTo("chat-shell: context broke" + System.lineSeparator())
    }

    @Test
    fun `a run that ends gives OK and adds no line`() {
        val err = StringWriter()

        assertThat(statusOfRun(PrintWriter(err)) { }).isEqualTo(ShellExitStatus.OK)
        assertThat(err.toString()).isEmpty()
    }

    /**
     * The AOT processor of Spring Boot runs `main` and stops the run on purpose
     * with this exception. A native build then reads the context it captured.
     * If `main` took the exception for a failure, the build would end with
     * "chat-shell: AbandonedRunException". Found by the native proof of task 7.
     */
    @Test
    fun `an abandoned run of the AOT processor passes through`() {
        assertThatThrownBy { statusOfRun(PrintWriter(StringWriter())) { throw SpringApplication.AbandonedRunException() } }
            .isInstanceOf(SpringApplication.AbandonedRunException::class.java)
    }

    @Test
    fun `a failed run gives its status`() {
        val err = StringWriter()

        val status = statusOfRun(PrintWriter(err)) { throw IllegalStateException("broke") }

        assertThat(status).isEqualTo(ShellExitStatus.FAILED)
        assertThat(err.toString()).isEqualTo("chat-shell: broke" + System.lineSeparator())
    }
}
