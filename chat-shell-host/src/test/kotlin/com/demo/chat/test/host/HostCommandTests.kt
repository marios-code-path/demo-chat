package com.demo.chat.test.host

import com.demo.chat.shell.host.BangCommandParser
import com.demo.chat.shell.host.HostCommand
import com.demo.chat.shell.host.HostCommandRegistrar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.springframework.shell.core.InputReader
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.DefaultCommandParser
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Path

/**
 * The `!` command runs one line with bash and prints the output and the exit
 * status. No case ends the shell. See CHAT-jrlctimq.
 */
class HostCommandTests {

    private fun run(host: HostCommand, line: String?): List<String> {
        val output = StringWriter()
        host.run(line, PrintWriter(output))
        return output.toString().lines().dropLastWhile { it.isEmpty() }
    }

    private fun run(line: String?): List<String> = run(HostCommand(), line)

    @Test
    fun `each output line prints and then the exit status`() {
        assertThat(run("echo a; echo b")).containsExactly("a", "b", "exit status 0")
    }

    @Test
    fun `a pipe runs in bash`() {
        assertThat(run("echo abc | tr a-z A-Z")).containsExactly("ABC", "exit status 0")
    }

    @Test
    fun `a non-zero status prints and the command returns`() {
        assertThat(run("echo before; exit 3")).containsExactly("before", "exit status 3")
    }

    @Test
    fun `standard error appears in the output`() {
        assertThat(run("echo problem 1>&2")).containsExactly("problem", "exit status 0")
    }

    @Test
    @Timeout(10)
    fun `a program that reads input gets end of input at once`() {
        assertThat(run("cat; echo done")).containsExactly("done", "exit status 0")
    }

    @Test
    fun `the default working directory is user dir`() {
        val expected = File(System.getProperty("user.dir")).canonicalPath

        assertThat(run("pwd -P")).containsExactly(expected, "exit status 0")
    }

    @Test
    fun `each line is flushed before the process ends`() {
        val flushed = mutableListOf<String>()
        val text = StringBuilder()
        val recording = object : Writer() {
            override fun write(cbuf: CharArray, off: Int, len: Int) {
                text.appendRange(cbuf, off, off + len)
            }
            override fun flush() {
                flushed += text.toString()
            }
            override fun close() {}
        }

        HostCommand().run("echo a; sleep 2; echo b", PrintWriter(recording))

        assertThat(flushed.first()).isEqualTo("a" + System.lineSeparator())
    }

    @Test
    fun `no bash on the PATH prints the no-bash line and starts nothing`(@TempDir empty: Path) {
        var started = false
        val host = HostCommand(path = empty.toString(), start = { started = true; it.start() })

        assertThat(run(host, "echo a")).containsExactly("! needs bash on the PATH. This shell has none.")
        assertThat(started).isFalse()
    }

    @Test
    fun `a failed start prints its reason`() {
        val host = HostCommand(start = { throw IOException("boom") })

        assertThat(run(host, "echo a")).containsExactly("! could not start bash: boom")
    }

    @Test
    fun `a typed bang line runs through the parser and the registered command`() {
        val registry = CommandRegistry(setOf(HostCommandRegistrar(HostCommand()).bangCommand()))
        val parsed = BangCommandParser(DefaultCommandParser(registry)).parse("!echo 'a  b' | tr a A")
        val output = StringWriter()

        registry.getCommandByName(parsed.commandName())!!
            .execute(CommandContext(parsed, registry, PrintWriter(output), mock(InputReader::class.java)))

        assertThat(output.toString().lines()).startsWith("A  b", "exit status 0")
    }

    @Test
    fun `the ! command declares its name, help, group, and no argument or option`() {
        // docs/SHELL-COMMAND-CONTRACT.md lists the command. chat-shell pins the
        // other 28 commands in ShellCommandContractTests.
        val command = HostCommandRegistrar(HostCommand()).bangCommand()

        assertThat(command.name).isEqualTo("!")
        assertThat(command.description).isEqualTo("Run a host command with bash")
        assertThat(command.group).isEqualTo("Host")
        assertThat(command.arguments).isEmpty()
        assertThat(command.options).isEmpty()
    }

    @Test
    fun `no argument prints the usage line`(@TempDir directory: Path) {
        val host = HostCommand(workingDirectory = directory.toFile())

        assertThat(run(host, null))
            .containsExactly("Usage: !<command>. Runs the rest of the line with bash in $directory.")
    }
}
