package com.demo.chat.shell.cli

import org.springframework.context.annotation.Profile
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandExecutor
import org.springframework.shell.core.command.CommandNotFoundException
import org.springframework.shell.core.command.CommandParser
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.ExitStatus
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.PrintWriter

/**
 * The one execution path of the one-shot and the resident modes.
 *
 * It parses one command line, refuses the forms that a mode cannot run, runs
 * the command through the registry, and returns one status of
 * [ShellExitStatus]. The command output goes to `out`. A failure goes to
 * `err` as one line, `chat-shell: <message>`, and nothing else goes to `err`.
 *
 * **`listen`, `hangup`, and `script` are refused.** They stream or hold state
 * across commands. **A first word that starts with `@` is refused too.** Spring
 * Shell reads such a word as a script file, and one run has one status.
 * **A first word that starts with `!` is refused too.** The host escape prints
 * the status of its bash command and returns none to Spring Shell, so a mode
 * would end with 0 after a failed host command.
 */
@Component
@Profile("shell")
class CommandLineExecution(private val parser: CommandParser, private val registry: CommandRegistry) {

    fun run(line: String, input: InputStream, out: PrintWriter, err: PrintWriter): ShellExitStatus {
        val status = try {
            execute(line, input, out)
        } catch (e: CommandNotFoundException) {
            fail(err, ShellExitStatus.USAGE, "unknown command ${e.commandName}")
        } catch (e: Exception) {
            fail(err, ShellExitStatus.of(e), ShellExitStatus.messageOf(e))
        }
        out.flush()
        err.flush()
        return status
    }

    private fun execute(line: String, input: InputStream, out: PrintWriter): ShellExitStatus {
        val word = line.trim().substringBefore(' ').substringBefore('\t')
        if (word.isEmpty()) throw ShellExit(ShellExitStatus.USAGE, "the command line is empty")
        if (word.startsWith("@")) throw ShellExit(ShellExitStatus.USAGE, "a script file is not allowed in this mode")
        if (word.startsWith("!")) throw ShellExit(ShellExitStatus.USAGE, "! is not allowed in this mode")
        if (word in REFUSED) throw ShellExit(ShellExitStatus.USAGE, "$word is not allowed in this mode")

        val parsed = try {
            parser.parse(line)
        } catch (e: Exception) {
            throw ShellExit(ShellExitStatus.USAGE, ShellExitStatus.messageOf(e))
        }
        val context = CommandContext(parsed, registry, out, StreamInput(input))
        val result = CommandExecutor(registry).execute(context)
        return when (result.code()) {
            ExitStatus.OK.code() -> ShellExitStatus.OK
            ExitStatus.USAGE_ERROR.code() -> ShellExitStatus.USAGE
            else -> ShellExitStatus.FAILED
        }
    }

    private fun fail(err: PrintWriter, status: ShellExitStatus, message: String): ShellExitStatus {
        err.println("chat-shell: $message")
        return status
    }

    private companion object {
        val REFUSED = setOf("listen", "hangup", "script")
    }
}
