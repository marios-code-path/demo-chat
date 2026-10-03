package com.demo.chat.test.commands

import com.demo.chat.domain.Key
import com.demo.chat.shell.commands.LoginCommands
import com.demo.chat.shell.commands.LoginCommandsRegistrar
import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.PubSubCommandsRegistrar
import com.demo.chat.shell.commands.TopicCommands
import com.demo.chat.shell.commands.TopicCommandsRegistrar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
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

    /** Runs one typed line and answers what the command printed. */
    private fun execute(beans: List<Command>, line: String, reader: InputReader = mock(InputReader::class.java)): String {
        val registry = CommandRegistry(beans.toSet())
        val parsed = DefaultCommandParser(registry).parse(line)
        val command = requireNotNull(registry.getCommandByName(parsed.commandName())) { "no command for: $line" }
        val output = StringWriter()
        command.execute(CommandContext(parsed, registry, PrintWriter(output), reader))
        return output.toString().trim()
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

    /** **A command that changes state confirms it.** It printed nothing before `CHAT-dxkkzvrf`. */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `add-topic prints the new room and its id`() {
        val commands = mock(TopicCommands::class.java) as TopicCommands<Any>
        given(commands.addTopic("_", "lobby")).willReturn(Key.of<Any>(7L, 5L))

        val printed = execute(commandsOf(TopicCommandsRegistrar(commands)), "add-topic --name lobby")

        assertThat(printed).isEqualTo("Created room lobby (id 7)")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `join and leave print the room name`() {
        val beans = commandsOf(TopicCommandsRegistrar(mock(TopicCommands::class.java) as TopicCommands<Any>))

        assertThat(execute(beans, "join --topicName lobby")).isEqualTo("Joined lobby")
        assertThat(execute(beans, "leave --topicName lobby")).isEqualTo("Left lobby")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `listen and hangup print the topic id`() {
        val beans = commandsOf(PubSubCommandsRegistrar(mock(PubSubCommands::class.java) as PubSubCommands<Any>))

        assertThat(execute(beans, "listen --topicId 9"))
            .isEqualTo("Listening to topic 9. Run hangup --topicId 9 to stop.")
        assertThat(execute(beans, "hangup --topicId 9")).isEqualTo("Stopped listening to topic 9")
    }

    /** **`exit` and `quit` run `bye`.** The registry falls back to the aliases. */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `exit and quit run bye`() {
        val commands = mock(LoginCommands::class.java) as LoginCommands<Any>
        val beans = commandsOf(LoginCommandsRegistrar(commands))

        execute(beans, "exit")
        execute(beans, "quit")

        verify(commands, times(2)).bye()
    }

    /**
     * **`login` prompts for an absent password.** The prompt does not echo, and
     * the password does not enter the command history.
     */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `login without a password prompts for it`() {
        val commands = mock(LoginCommands::class.java) as LoginCommands<Any>
        val reader = mock(InputReader::class.java)
        given(reader.readPassword("Password: ")).willReturn("changeme".toCharArray())

        val printed = execute(commandsOf(LoginCommandsRegistrar(commands)), "login --username Admin", reader)

        verify(commands).login("Admin", "changeme")
        assertThat(printed).isEqualTo("Logged in as Admin")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `login with a typed password does not prompt`() {
        val commands = mock(LoginCommands::class.java) as LoginCommands<Any>
        val reader = mock(InputReader::class.java)

        execute(commandsOf(LoginCommandsRegistrar(commands)), "login --username Admin --password changeme", reader)

        verify(commands).login("Admin", "changeme")
        verify(reader, never()).readPassword(anyString())
    }

    /** **No password, no login.** A closed input answers null at the prompt. */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `login with no password at the prompt is refused`() {
        val commands = mock(LoginCommands::class.java) as LoginCommands<Any>
        val reader = mock(InputReader::class.java)

        val printed = execute(commandsOf(LoginCommandsRegistrar(commands)), "login --username Admin", reader)

        assertThat(printed).isEqualTo("Login needs a password.")
        verify(commands, never()).login(anyString(), anyString())
    }
}
