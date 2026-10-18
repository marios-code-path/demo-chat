package com.demo.chat.test.cli

import com.demo.chat.security.rsocket.CoreAuthenticationRefusal
import com.demo.chat.shell.cli.CommandLineExecution
import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import com.demo.chat.shell.cli.StreamInput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.DefaultCommandParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.ConnectException
import java.util.function.Function

/**
 * The one execution path of the one-shot and the resident modes. It maps
 * every outcome to one status of the exit table, and it writes each failure
 * as one line on the error writer. See CHAT-jmnjvupr.
 */
class CommandLineExecutionTests {

    private class Outcome(val status: ShellExitStatus, val out: String, val err: String)

    private fun command(name: String, body: (CommandContext) -> String): Command =
        Command.builder().name(name).group("Test").execute(Function<CommandContext, String> { body(it) })

    private fun execution(vararg commands: Command): CommandLineExecution {
        val registry = CommandRegistry(commands.toSet())
        return CommandLineExecution(DefaultCommandParser(registry), registry)
    }

    private fun run(
        execution: CommandLineExecution,
        line: String,
        input: InputStream = ByteArrayInputStream(ByteArray(0)),
    ): Outcome {
        val out = StringWriter()
        val err = StringWriter()
        val status = execution.run(line, input, PrintWriter(out), PrintWriter(err))
        return Outcome(status, out.toString(), err.toString())
    }

    @Test
    fun `a command result goes to the writer, and the status is OK`() {
        val outcome = run(execution(command("echo-ish") { "hi" }), "echo-ish")

        assertThat(outcome.status).isEqualTo(ShellExitStatus.OK)
        assertThat(outcome.out).isEqualTo("hi" + System.lineSeparator())
        assertThat(outcome.err).isEmpty()
    }

    @Test
    fun `a blank line is refused with USAGE`() {
        val outcome = run(execution(command("echo-ish") { "hi" }), "   ")

        assertThat(outcome.status).isEqualTo(ShellExitStatus.USAGE)
        assertThat(outcome.out).isEmpty()
        assertThat(outcome.err).isEqualTo("chat-shell: the command line is empty" + System.lineSeparator())
    }

    @Test
    fun `a first word that starts with @ is refused with USAGE`() {
        val outcome = run(execution(command("echo-ish") { "hi" }), "@/tmp/x")

        assertThat(outcome.status).isEqualTo(ShellExitStatus.USAGE)
        assertThat(outcome.err)
            .isEqualTo("chat-shell: a script file is not allowed in this mode" + System.lineSeparator())
    }

    @Test
    fun `listen, hangup, and script are refused with USAGE`() {
        for (name in listOf("listen", "hangup", "script")) {
            var called = false
            val outcome = run(execution(command(name) { called = true; "x" }), "$name lobby")

            assertThat(outcome.status).describedAs(name).isEqualTo(ShellExitStatus.USAGE)
            assertThat(outcome.err).describedAs(name)
                .isEqualTo("chat-shell: $name is not allowed in this mode" + System.lineSeparator())
            assertThat(called).describedAs(name).isFalse()
        }
    }

    /**
     * The `!` command prints the status of its bash command and returns no
     * status to Spring Shell. So a mode that ran it would end with 0 after a
     * failed host command. The caller has a shell of its own, so the modes
     * refuse it. Sigma found this on PR #259.
     */
    @Test
    fun `the host escape is refused with USAGE, with or without a space`() {
        for (line in listOf("! false", "!false", "!  ls -l")) {
            var called = false
            val outcome = run(execution(command("!") { called = true; "x" }), line)

            assertThat(outcome.status).describedAs(line).isEqualTo(ShellExitStatus.USAGE)
            assertThat(outcome.err).describedAs(line)
                .isEqualTo("chat-shell: ! is not allowed in this mode" + System.lineSeparator())
            assertThat(called).describedAs(line).isFalse()
        }
    }

    @Test
    fun `an unknown command gives USAGE`() {
        val outcome = run(execution(command("echo-ish") { "hi" }), "no-such-command")

        assertThat(outcome.status).isEqualTo(ShellExitStatus.USAGE)
        assertThat(outcome.err).isEqualTo("chat-shell: unknown command no-such-command" + System.lineSeparator())
    }

    @Test
    fun `an IllegalArgumentException gives USAGE and one err line`() {
        val outcome = run(
            execution(command("bad") { throw IllegalArgumentException("bad needs --topic.\nsecond line") }),
            "bad",
        )

        assertThat(outcome.status).isEqualTo(ShellExitStatus.USAGE)
        assertThat(outcome.out).isEmpty()
        assertThat(outcome.err).isEqualTo("chat-shell: bad needs --topic." + System.lineSeparator())
    }

    @Test
    fun `a CoreAuthenticationRefusal anywhere in the causes gives LOGIN`() {
        val outcome = run(
            execution(command("deny") { throw IllegalStateException("outer", CoreAuthenticationRefusal(RuntimeException("Invalid Credentials"))) }),
            "deny",
        )

        assertThat(outcome.status).isEqualTo(ShellExitStatus.LOGIN)
        assertThat(outcome.err).startsWith("chat-shell: ")
        assertThat(outcome.err.lines().filter { it.isNotEmpty() }).hasSize(1)
    }

    @Test
    fun `a ConnectException anywhere in the causes gives UNAVAILABLE`() {
        val outcome = run(
            execution(command("down") { throw RuntimeException("wrapped", ConnectException("Connection refused")) }),
            "down",
        )

        assertThat(outcome.status).isEqualTo(ShellExitStatus.UNAVAILABLE)
        assertThat(outcome.err).isEqualTo("chat-shell: Connection refused" + System.lineSeparator())
    }

    @Test
    fun `any other error gives FAILED`() {
        val outcome = run(execution(command("boom") { throw IllegalStateException("no such room") }), "boom")

        assertThat(outcome.status).isEqualTo(ShellExitStatus.FAILED)
        assertThat(outcome.err).isEqualTo("chat-shell: no such room" + System.lineSeparator())
    }

    @Test
    fun `a ShellExit gives its own status`() {
        val outcome = run(
            execution(command("late") { throw ShellExit(ShellExitStatus.NOT_CONFIRMED, "no read showed the post") }),
            "late",
        )

        assertThat(outcome.status).isEqualTo(ShellExitStatus.NOT_CONFIRMED)
        assertThat(outcome.err).isEqualTo("chat-shell: no read showed the post" + System.lineSeparator())
    }

    @Test
    fun `the command reads the whole input through StreamInput`() {
        var seen: String? = null
        val reader = command("slurp") { ctx ->
            seen = (ctx.inputReader() as StreamInput).readAll()
            "done"
        }

        val outcome = run(execution(reader), "slurp", ByteArrayInputStream("a\r\nb".toByteArray()))

        assertThat(outcome.status).isEqualTo(ShellExitStatus.OK)
        assertThat(seen).isEqualTo("a\r\nb")
    }
}
