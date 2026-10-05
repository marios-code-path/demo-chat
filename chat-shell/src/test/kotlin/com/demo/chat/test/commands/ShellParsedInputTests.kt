package com.demo.chat.test.commands

import com.demo.chat.domain.Key
import com.demo.chat.shell.commands.LoginCommands
import com.demo.chat.shell.commands.LoginCommandsRegistrar
import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.PubSubCommandsRegistrar
import com.demo.chat.shell.commands.TopicCommands
import com.demo.chat.shell.commands.TopicCommandsRegistrar
import com.demo.chat.shell.commands.UserCommands
import com.demo.chat.shell.commands.UserCommandsRegistrar
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.isNull
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verifyNoInteractions
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

        execute(commandsOf(TopicCommandsRegistrar(commands)), "join --topic lobby")

        verify(commands).join("_", "lobby")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `send with only a topic and a text takes every other default`() {
        val commands = mock(PubSubCommands::class.java) as PubSubCommands<Any>

        execute(commandsOf(PubSubCommandsRegistrar(commands)), "send --topic lobby --messageText hello")

        verify(commands).send("lobby", "_", "hello")
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

        assertThat(execute(beans, "join --topic lobby")).isEqualTo("Joined lobby")
        assertThat(execute(beans, "leave --topic lobby")).isEqualTo("Left lobby")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `listen and hangup print the resolved topic id`() {
        val commands = mock(PubSubCommands::class.java) as PubSubCommands<Any>
        given(commands.listen("lobby")).willReturn("9")
        given(commands.hangup("lobby")).willReturn("9")
        val beans = commandsOf(PubSubCommandsRegistrar(commands))

        assertThat(execute(beans, "listen --topic lobby"))
            .isEqualTo("Listening to topic 9. Run hangup 9 to stop.")
        assertThat(execute(beans, "hangup --topic lobby")).isEqualTo("Stopped listening to topic 9")
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

    /**
     * **Each command takes its main option as its first bare word.** The owner
     * met `add-topic lobby` and `list-members <id>` refused on 2026-10-02. See
     * `CHAT-lasqmeib`.
     */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `every topic command takes its main option by position`() {
        val commands = mock(TopicCommands::class.java) as TopicCommands<Any>
        val beans = commandsOf(TopicCommandsRegistrar(commands))

        execute(beans, "add-topic lobby")
        execute(beans, "topic-by-name lobby")
        execute(beans, "join lobby")
        execute(beans, "leave 1555400854075346944")
        execute(beans, "member-of 42")
        execute(beans, "list-members 1555400854075346944")

        verify(commands).addTopic("_", "lobby")
        verify(commands).topicByName("_", "lobby")
        verify(commands).join("_", "lobby")
        verify(commands).leave("_", "1555400854075346944")
        verify(commands).memberOf("42")
        verify(commands).listMembers("1555400854075346944")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `every pubsub command takes its main option by position`() {
        val commands = mock(PubSubCommands::class.java) as PubSubCommands<Any>
        given(commands.messages(anyString(), isNull())).willReturn("")
        val beans = commandsOf(PubSubCommandsRegistrar(commands))

        execute(beans, "send --topic lobby \"hello there\"")
        execute(beans, "listen lobby")
        execute(beans, "hangup lobby")
        execute(beans, "messages lobby")

        verify(commands).send("lobby", "_", "hello there")
        verify(commands).listen("lobby")
        verify(commands).hangup("lobby")
        verify(commands).messages("lobby")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `every user command takes its main option by position`() {
        val commands = mock(UserCommands::class.java) as UserCommands<Any>
        given(commands.keyOf("7")).willReturn(7L)
        val beans = commandsOf(UserCommandsRegistrar(commands))

        execute(beans, "kv apple")
        execute(beans, "get-k-v 7")
        execute(beans, "find-user Admin")
        execute(beans, "get-user Admin")
        execute(beans, "passwd changeme")
        execute(beans, "get-permissions-for-user 42")

        verify(commands).kv("apple")
        verify(commands).getKV(7L)
        verify(commands).findUser("Admin")
        verify(commands).getUser("Admin")
        verify(commands).passwd("_", "changeme")
        verify(commands).getPermissionsForUser("42")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `login takes the username by position and prompts for the password`() {
        val commands = mock(LoginCommands::class.java) as LoginCommands<Any>
        val reader = mock(InputReader::class.java)
        given(reader.readPassword("Password: ")).willReturn("changeme".toCharArray())

        execute(commandsOf(LoginCommandsRegistrar(commands)), "login Admin", reader)

        verify(commands).login("Admin", "changeme")
    }

    /** **The named option still works.** Both forms reach the same call. */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `the named option and the bare word reach the same call`() {
        val commands = mock(TopicCommands::class.java) as TopicCommands<Any>
        val beans = commandsOf(TopicCommandsRegistrar(commands))

        execute(beans, "list-members --topic lobby")
        execute(beans, "list-members lobby")

        verify(commands, times(2)).listMembers("lobby")
    }

    /**
     * **A missing main value is refused with one message.** It answered an
     * empty text before, so `add-topic` with no name reached the server.
     */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `a missing main value is refused and names both forms`() {
        val commands = mock(TopicCommands::class.java) as TopicCommands<Any>

        assertThatThrownBy { execute(commandsOf(TopicCommandsRegistrar(commands)), "add-topic") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("add-topic needs name. Give it as the first argument or as --name.")
        verify(commands, never()).addTopic(anyString(), anyString())
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `send with no text is refused`() {
        val commands = mock(PubSubCommands::class.java) as PubSubCommands<Any>

        assertThatThrownBy { execute(commandsOf(PubSubCommandsRegistrar(commands)), "send --topic lobby") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("send needs messageText. Give it as the first argument or as --messageText.")
        verify(commands, never()).send(anyString(), anyString(), anyString())
    }

    /** **A required option that is not the main option is refused too.** */
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `a missing required option is refused`() {
        val commands = mock(UserCommands::class.java) as UserCommands<Any>

        assertThatThrownBy { execute(commandsOf(UserCommandsRegistrar(commands)), "add-user --name Ann --handle ann") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("add-user needs --imageUri.")
        verify(commands, never()).addUser(anyString(), anyString(), anyString())
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `a main value given two ways is refused`() {
        val commands = mock(TopicCommands::class.java) as TopicCommands<Any>

        assertThatThrownBy { execute(commandsOf(TopicCommandsRegistrar(commands)), "join --topic lobby hall") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Give topic one time, as --topic or as the first argument.")
        verify(commands, never()).join(anyString(), anyString())
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `two bare words are refused`() {
        val commands = mock(TopicCommands::class.java) as TopicCommands<Any>

        assertThatThrownBy { execute(commandsOf(TopicCommandsRegistrar(commands)), "add-topic big room") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("add-topic takes one argument. Put quotes around a value that has spaces.")
        verify(commands, never()).addTopic(anyString(), anyString())
    }
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `messages passes its optional limit through the real parser`() {
        val commands = mock(PubSubCommands::class.java) as PubSubCommands<Any>
        val beans = commandsOf(PubSubCommandsRegistrar(commands))

        execute(beans, "messages lobby --limit 2")
        execute(beans, "messages --topic lobby --limit 3")

        verify(commands).messages("lobby", 2)
        verify(commands).messages("lobby", 3)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `messages refuses a malformed limit before calling the service`() {
        val commands = mock(PubSubCommands::class.java) as PubSubCommands<Any>
        val beans = commandsOf(PubSubCommandsRegistrar(commands))

        listOf("text", "2147483648").forEach { value ->
            assertThatThrownBy { execute(beans, "messages lobby --limit $value") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("messages needs a positive --limit.")
        }
        verifyNoInteractions(commands)
    }

}
