package com.demo.chat.test.commands

import com.demo.chat.shell.commands.LoginCommands
import com.demo.chat.shell.commands.LoginCommandsRegistrar
import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.PubSubCommandsRegistrar
import com.demo.chat.shell.commands.TopicCommands
import com.demo.chat.shell.commands.TopicCommandsRegistrar
import com.demo.chat.shell.commands.UserCommands
import com.demo.chat.shell.commands.UserCommandsRegistrar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.shell.core.InputReader
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandOption
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.ParsedInput
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Each command runs non-interactively and reaches the right method.
 *
 * **The contract test checks the shape, and the group tests check the
 * logic. Neither checks the wiring between them.**
 * `ShellCommandContractTests` reads names, defaults and required flags off
 * the `Command` beans. `ShellUserCommandsTests` and its siblings call the
 * command bean methods directly. So a command that read the wrong option,
 * or passed its two options in the wrong order, would pass both.
 *
 * These tests execute the `Command` the registrar built, against a real
 * `CommandContext`, and assert which method the command bean received and
 * with what. That is the seam `ctx.optionValue(...)` sits on.
 *
 * The default path matters as much as the supplied path. Spring Shell 4
 * binds nothing, so `optionValue` is the only thing that turns an absent
 * option into its declared default. A command tested only with every option
 * supplied would never exercise it. See CHAT-fxrwtvef.
 */
class ShellCommandDispatchTests {

    private fun context(command: Command, supplied: Map<String, String>): CommandContext {
        // The context carries the command's own declared options, with a
        // value only where the caller supplied one. That is what the parser
        // hands a command at runtime.
        val options = command.options.map { declared ->
            CommandOption.with()
                .longName(requireNotNull(declared.longName()) { "a declared option has no long name" })
                .required(declared.required() ?: false)
                .type(declared.type())
                .apply {
                    declared.defaultValue()?.let { defaultValue(it) }
                    supplied[declared.longName()]?.let { value(it) }
                }
                .build()
        }

        val input = ParsedInput.builder().commandName(command.name)
        options.forEach { input.addOption(it) }

        return CommandContext(
            input.build(),
            mock(CommandRegistry::class.java),
            PrintWriter(StringWriter()),
            mock(InputReader::class.java),
        )
    }

    private fun run(command: Command, supplied: Map<String, String> = emptyMap()) {
        command.execute(context(command, supplied))
    }

    private fun commandOf(beans: List<Command>, name: String): Command =
        beans.first { it.name == name }

    @Suppress("UNCHECKED_CAST")
    private fun topicBeans(commands: TopicCommands<Any>): List<Command> {
        val registrar = TopicCommandsRegistrar(commands)
        return registrar.javaClass.declaredMethods
            .filter { Command::class.java.isAssignableFrom(it.returnType) }
            .map { it.invoke(registrar) as Command }
    }

    @Test
    fun `a topic command passes its options in the declared order`() {
        val commands = mock(TopicCommands::class.java) as TopicCommands<Any>
        val beans = topicBeans(commands)

        run(commandOf(beans, "add-topic"), mapOf("userId" to "user-7", "name" to "a-room"))

        verify(commands).addTopic("user-7", "a-room")
    }

    @Test
    fun `an absent option falls back to its declared default`() {
        val commands = mock(TopicCommands::class.java) as TopicCommands<Any>
        val beans = topicBeans(commands)

        // userId declares the default "_" and the caller supplies nothing.
        run(commandOf(beans, "add-topic"), mapOf("name" to "a-room"))

        verify(commands).addTopic("_", "a-room")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `a login command reaches its method`() {
        val commands = mock(LoginCommands::class.java) as LoginCommands<Any>
        val registrar = LoginCommandsRegistrar(commands)
        val beans = registrar.javaClass.declaredMethods
            .filter { Command::class.java.isAssignableFrom(it.returnType) }
            .map { it.invoke(registrar) as Command }

        run(commandOf(beans, "login"), mapOf("username" to "alice", "password" to "secret"))

        verify(commands).login("alice", "secret")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `a pubsub command reaches its method with a default and a value`() {
        val commands = mock(PubSubCommands::class.java) as PubSubCommands<Any>
        val registrar = PubSubCommandsRegistrar(commands)
        val beans = registrar.javaClass.declaredMethods
            .filter { Command::class.java.isAssignableFrom(it.returnType) }
            .map { it.invoke(registrar) as Command }

        run(commandOf(beans, "hangup"), mapOf("topicId" to "topic-3"))

        verify(commands).hangup("topic-3")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `a user command reaches its method`() {
        val commands = mock(UserCommands::class.java) as UserCommands<Any>
        val registrar = UserCommandsRegistrar(commands)
        val beans = registrar.javaClass.declaredMethods
            .filter { Command::class.java.isAssignableFrom(it.returnType) }
            .map { it.invoke(registrar) as Command }

        run(commandOf(beans, "kv"), mapOf("value" to "payload"))

        verify(commands).kv("payload")
    }

    @Test
    fun `every command executes without a missing option`() {
        // optionValue fails loudly when a command reads an option it never
        // declared. Running every command with only its defaults proves no
        // command names an option that is not on it.
        val topic = mock(TopicCommands::class.java) as TopicCommands<Any>
        val login = mock(LoginCommands::class.java) as LoginCommands<Any>
        val pubsub = mock(PubSubCommands::class.java) as PubSubCommands<Any>
        val user = mock(UserCommands::class.java) as UserCommands<Any>

        val registrars = listOf<Any>(
            TopicCommandsRegistrar(topic),
            LoginCommandsRegistrar(login),
            PubSubCommandsRegistrar(pubsub),
            UserCommandsRegistrar(user),
        )

        val beans = registrars.flatMap { registrar ->
            registrar.javaClass.declaredMethods
                .filter { Command::class.java.isAssignableFrom(it.returnType) }
                .map { it.invoke(registrar) as Command }
        }

        assertThat(beans).hasSize(26)
        beans.forEach { command -> run(command) }
    }
}
