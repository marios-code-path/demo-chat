package com.demo.chat.test.commands

import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.PubSubCommandsRegistrar
import com.demo.chat.shell.commands.TopicCommands
import com.demo.chat.shell.commands.TopicCommandsRegistrar
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.shell.core.InputReader
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.DefaultCommandParser
import java.io.PrintWriter
import java.io.StringWriter

/**
 * A typed command line runs through the real Spring Shell parser.
 *
 * **The parser adds only the options that the user typed.** `DefaultCommandParser`
 * never reads a declared default, and `CommandContext` looks an option up in the
 * parsed input alone. So an optional option that the user leaves out is absent
 * at runtime. `ShellCommandDispatchTests` builds its context from every declared
 * option, so it could not see this. The owner met it on 2026-10-02: `join`
 * asked for `--userId`, and `send` asked for every option. See `CHAT-xdpcnrde`.
 */
class ShellParsedInputTests {

    private fun execute(beans: List<Command>, line: String) {
        val registry = CommandRegistry(beans.toSet())
        val parsed = DefaultCommandParser(registry).parse(line)
        val command = requireNotNull(registry.getCommandByName(parsed.commandName())) { "no command for: $line" }
        command.execute(CommandContext(parsed, registry, PrintWriter(StringWriter()), mock(InputReader::class.java)))
    }

    private fun commandsOf(registrar: Any): List<Command> =
        registrar.javaClass.declaredMethods
            .filter { Command::class.java.isAssignableFrom(it.returnType) }
            .map { it.invoke(registrar) as Command }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `join with only a topic name takes the current user`() {
        val commands = mock(TopicCommands::class.java) as TopicCommands<Any>

        execute(commandsOf(TopicCommandsRegistrar(commands)), "join --topicName lobby")

        verify(commands).join("_", "lobby")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `send with only a topic name and a text takes every other default`() {
        val commands = mock(PubSubCommands::class.java) as PubSubCommands<Any>

        execute(commandsOf(PubSubCommandsRegistrar(commands)), "send --topicName lobby --messageText hello")

        verify(commands).send("lobby", "_", "_", "hello")
    }
}
