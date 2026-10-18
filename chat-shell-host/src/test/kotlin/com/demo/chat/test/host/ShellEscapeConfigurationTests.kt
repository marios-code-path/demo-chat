package com.demo.chat.test.host

import com.demo.chat.shell.host.BangCommandParser
import com.demo.chat.shell.host.HostCommand
import com.demo.chat.shell.host.HostCommandRegistrar
import com.demo.chat.shell.host.ShellEscapeConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.jline.reader.LineReader
import org.jline.terminal.Terminal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import org.springframework.shell.core.InputReader
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandParser
import org.springframework.shell.core.command.CommandRegistry
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path

/**
 * The `!` escape reaches the interactive runner, the line reader, and
 * `script -f`.
 *
 * **A script line changes before any parser receives it.** `FileInputProvider`
 * joins a line that ends with `\` and blanks to the next line. `Script` splits
 * the line on single blanks and the runner joins the words again, so trailing
 * blanks are lost. The last case pins that limit. A Spring Shell change that
 * keeps the line fails it, and docs/SHELL-COMMAND-CONTRACT.md then changes.
 * See CHAT-jrlctimq.
 */
class ShellEscapeConfigurationTests {

    private val contexts = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SpringShellAutoConfiguration::class.java))
        .withInitializer { it.environment.setActiveProfiles("shell") }
        .withPropertyValues("spring.shell.interactive.enabled=true")
        .withUserConfiguration(ShellEscapeConfiguration::class.java, HostCommandRegistrar::class.java)

    private fun script(context: ApplicationContext, lines: String, directory: Path): List<String> {
        val file = Files.writeString(directory.resolve("lines.txt"), lines)
        val registry = context.getBean(CommandRegistry::class.java)
        val parsed = context.getBean(CommandParser::class.java).parse("script -f $file")
        val output = StringWriter()
        registry.getCommandByName(parsed.commandName())!!
            .execute(CommandContext(parsed, registry, PrintWriter(output), mock(InputReader::class.java)))
        return output.toString().lines().dropLastWhile { it.isEmpty() }
    }

    @Test
    fun `the command parser is the bang parser`() {
        contexts.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(CommandParser::class.java)).isInstanceOf(BangCommandParser::class.java)
        }
    }

    @Test
    fun `the line reader has event expansion off`() {
        contexts.run { context ->
            assertThat(context.getBean(LineReader::class.java).isSet(LineReader.Option.DISABLE_EVENT_EXPANSION))
                .isTrue()
        }
    }

    @Test
    fun `a script file runs a bang line`(@TempDir directory: Path) {
        contexts.run { context ->
            assertThat(script(context, "!echo a\n", directory)).containsExactly("a", "exit status 0")
        }
    }

    @Test
    fun `a script bang line keeps its inner blanks`(@TempDir directory: Path) {
        contexts.run { context ->
            assertThat(script(context, "!printf '[%s]\\n' 'x  y'\n", directory))
                .containsExactly("[x  y]", "exit status 0")
        }
    }

    @Test
    fun `a script line that ends with an escaped blank joins the next line`(@TempDir directory: Path) {
        contexts.run { context ->
            assertThat(script(context, "!printf '[%s]\\n' a\\ \n!echo next\n", directory))
                .containsExactly("[a]", "[!echo]", "[next]", "exit status 0")
        }
    }

    @Test
    @Timeout(20)
    fun `INT on the shell terminal stops a running bang command`() {
        contexts.run { context ->
            val terminal = context.getBean(Terminal::class.java)
            val text = StringBuilder()
            var sent = false
            val writer = object : Writer() {
                override fun write(cbuf: CharArray, off: Int, len: Int) {
                    text.appendRange(cbuf, off, off + len)
                }
                override fun flush() {
                    if (!sent && text.contains("started")) {
                        sent = true
                        terminal.raise(Terminal.Signal.INT)
                    }
                }
                override fun close() {}
            }

            context.getBean(HostCommand::class.java).run("echo started; sleep 30", PrintWriter(writer))

            assertThat(text.lines()).contains("! interrupted")
        }
    }
}
