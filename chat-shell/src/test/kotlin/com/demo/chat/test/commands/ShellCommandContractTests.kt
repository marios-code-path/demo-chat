package com.demo.chat.test.commands

import com.demo.chat.shell.commands.LoginCommandsRegistrar
import com.demo.chat.shell.commands.PubSubCommandsRegistrar
import com.demo.chat.shell.commands.TopicCommandsRegistrar
import com.demo.chat.shell.commands.UserCommandsRegistrar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.shell.core.command.Command

/**
 * The command surface must match `docs/SHELL-COMMAND-CONTRACT.md`.
 *
 * **Spring Shell 4 derives no names, so a rename is a silent change.** The
 * declarative model produced each command name from a method name and each
 * option name from a parameter name. The programmatic model states them, and
 * a typing mistake would ship a different CLI with no compile error.
 *
 * This test holds the table that the document holds. A command that is added,
 * removed or renamed fails here. See CHAT-fxrwtvef.
 */
class ShellCommandContractTests {

    private data class Opt(val name: String, val default: String?)
    private data class Expected(val name: String, val description: String, val options: List<Opt>)

    private val expected = listOf(
        Expected("bye", "bye", listOf()),
        Expected("root-keys", "rootkeys", listOf()),
        Expected("whoami", "whoami", listOf()),
        Expected("login", "login", listOf(Opt("username", null), Opt("password", null))),
        Expected("send", "Send a Message", listOf(Opt("topicName", "_"), Opt("topicId", "_"), Opt("userName", "_"), Opt("messageText", null))),
        Expected("listen", "Listen to a topic", listOf(Opt("topicId", null))),
        Expected("hangup", "Stop listening to a topic", listOf(Opt("topicId", null))),
        Expected("show-topics", "show topics", listOf()),
        Expected("add-topic", "Create a topic", listOf(Opt("userId", "_"), Opt("name", null))),
        Expected("topic-by-name", "Topic by Name", listOf(Opt("userId", "_"), Opt("name", null))),
        Expected("join", "Subscribe to a topic", listOf(Opt("userId", "_"), Opt("topicName", null))),
        Expected("leave", "unSubscribe to a topic", listOf(Opt("userId", "_"), Opt("topicName", null))),
        Expected("member-of", "Show what topics user is subscribed to", listOf(Opt("userId", "_"))),
        Expected("list-members", "Show Subscribers on a topic", listOf(Opt("topicName", null))),
        Expected("kv", "Create a KeyValue", listOf(Opt("value", null))),
        Expected("get-k-v", "Get a KeyValue by Key ID", listOf(Opt("key", null))),
        Expected("all-k-v", "Get all KV", listOf()),
        Expected("key", "Create a Key", listOf()),
        Expected("add-user", "Add A User", listOf(Opt("name", null), Opt("handle", null), Opt("imageUri", null))),
        Expected("users", "All Users", listOf()),
        Expected("find-user", "Find a user", listOf(Opt("handle", null))),
        Expected("get-user", "Get a user", listOf(Opt("handle", null))),
        Expected("passwd", "Change User Password", listOf(Opt("userId", "_"), Opt("password", null))),
        Expected("get-permissions-for-user", "Gets user Permissions", listOf(Opt("userId", "_"))),
        Expected("all-permissions", "Get all Perms", listOf()),
        Expected("add-permission", "Add a User Permission", listOf(Opt("userId", "_"), Opt("targetUserId", null), Opt("role", null), Opt("expireTime", null))),
    )

    private fun registered(): List<Command> {
        val login = LoginCommandsRegistrar(mock(com.demo.chat.shell.commands.LoginCommands::class.java) as com.demo.chat.shell.commands.LoginCommands<Any>)
        val pubsub = PubSubCommandsRegistrar(mock(com.demo.chat.shell.commands.PubSubCommands::class.java) as com.demo.chat.shell.commands.PubSubCommands<Any>)
        val topic = TopicCommandsRegistrar(mock(com.demo.chat.shell.commands.TopicCommands::class.java) as com.demo.chat.shell.commands.TopicCommands<Any>)
        val user = UserCommandsRegistrar(mock(com.demo.chat.shell.commands.UserCommands::class.java) as com.demo.chat.shell.commands.UserCommands<Any>)

        return listOf<Any>(login, pubsub, topic, user).flatMap { registrar ->
            registrar.javaClass.declaredMethods
                .filter { Command::class.java.isAssignableFrom(it.returnType) }
                .map { it.invoke(registrar) as Command }
        }
    }

    @Test
    fun `every command name and option name matches the contract`() {
        val actual = registered().associateBy { it.name }

        assertThat(actual.keys)
            .describedAs("the command names of docs/SHELL-COMMAND-CONTRACT.md")
            .containsExactlyInAnyOrderElementsOf(expected.map { it.name })

        expected.forEach { want ->
            val got = actual.getValue(want.name)

            assertThat(got.description)
                .describedAs("the help text of %s", want.name)
                .isEqualTo(want.description)

            assertThat(got.options.map { it.longName() })
                .describedAs("the option names of %s", want.name)
                .containsExactlyElementsOf(want.options.map { it.name })

            want.options.forEach { option ->
                val actualOption = got.options.first { it.longName() == option.name }

                assertThat(actualOption.defaultValue())
                    .describedAs("the default of --%s on %s", option.name, want.name)
                    .isEqualTo(option.default)

                assertThat(actualOption.required())
                    .describedAs("whether --%s on %s is required", option.name, want.name)
                    .isEqualTo(option.default == null)
            }
        }
    }

    @Test
    fun `the contract holds twenty six commands and thirty two options`() {
        // The counts come from the same extraction that wrote the document.
        // They fail if a command or an option is dropped during a later edit.
        assertThat(registered()).hasSize(26)
        assertThat(registered().sumOf { it.options.size }).isEqualTo(32)
    }
}
