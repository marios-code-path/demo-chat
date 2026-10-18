package com.demo.chat.test.cli

import com.demo.chat.reportFailure
import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
}
