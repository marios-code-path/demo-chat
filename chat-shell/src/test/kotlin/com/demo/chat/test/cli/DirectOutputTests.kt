package com.demo.chat.test.cli

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.CoreServices
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.User
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.BackendState
import com.demo.chat.domain.command.BackendStatus
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.CommandStatus
import com.demo.chat.domain.command.MessageSendResult
import com.demo.chat.domain.command.Receipt
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.security.AuthorizationService
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.service.core.IKeyService
import com.demo.chat.shell.cli.CommandLineExecution
import com.demo.chat.shell.commands.ClaudeImportCommands
import com.demo.chat.shell.commands.ClaudeImportCommandsRegistrar
import com.demo.chat.shell.commands.LoginCommands
import com.demo.chat.shell.commands.LoginCommandsRegistrar
import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.PubSubCommandsRegistrar
import com.demo.chat.shell.commands.ShellRooms
import com.demo.chat.shell.commands.TopicCommands
import com.demo.chat.shell.commands.TopicCommandsRegistrar
import com.demo.chat.shell.commands.UserCommands
import com.demo.chat.shell.commands.UserCommandsRegistrar
import com.demo.chat.test.anyObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.shell.core.command.Clear
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.DefaultCommandParser
import org.springframework.shell.core.command.Help
import org.springframework.shell.jline.command.History
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Every command that the shell modes can run writes through the writer of its
 * context, and never to `System.out`.
 *
 * **A direct print is output that a mode cannot capture.** The one-shot
 * process would print it before the status, and a resident response would
 * lose it. The scan test fails on any direct print in the main source, so a
 * later `println` fails here and not in the field. See CHAT-jmnjvupr.
 */
@Suppress("UNCHECKED_CAST")
@Timeout(60)
class DirectOutputTests {
    private val messages = mock(ChatMessageService::class.java) as ChatMessageService<Long, String>
    private val users = mock(ChatUserService::class.java) as ChatUserService<Long>
    private val topics = mock(ChatTopicService::class.java) as ChatTopicService<Long, String>
    private val beans = mock(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>

    /** The commands that each mode refuses. They hold state across commands. */
    private val refused = setOf("listen", "hangup", "script")

    /**
     * One command line for each command that the modes run.
     *
     * **The `!` command is not here.** It lives in `chat-shell-host`, which
     * `chat-shell` does not depend on, because a shell build can leave the host
     * module out. `HostCommandTests` in that module checks its output path.
     */
    private val lines = mapOf(
        "root-keys" to "root-keys",
        "whoami" to "whoami",
        "login" to "login --username nobody --password secret",
        "send" to "send hello --topic lobby",
        "command-status" to "command-status --commandId c-1",
        "register-read" to "register-read --index register-index",
        "post" to "post lobby",
        "messages" to "messages lobby",
        "import-claude" to "import-claude --file /nonexistent.json --conversation c --topic t --generation 1 --human h --assistant a",
        "show-topics" to "show-topics",
        "add-topic" to "add-topic room",
        "topic-by-name" to "topic-by-name room",
        "join" to "join lobby",
        "leave" to "leave lobby",
        "member-of" to "member-of 1",
        "list-members" to "list-members lobby",
        "kv" to "kv value",
        "get-k-v" to "get-k-v 1",
        "all-k-v" to "all-k-v",
        "key" to "key",
        "add-user" to "add-user --name n --handle h --imageUri u",
        "users" to "users",
        "find-user" to "find-user h",
        "get-user" to "get-user ghost",
        "passwd" to "passwd secret",
        "get-permissions-for-user" to "get-permissions-for-user 1",
        "all-permissions" to "all-permissions",
        "add-permission" to "add-permission --targetUserId 1 --role ROLE --expireTime 1",
        "help" to "help",
        "clear" to "clear",
        "history" to "history",
    )

    private fun registry(): CommandRegistry {
        given(beans.messageService()).willReturn(messages)
        given(beans.userService()).willReturn(users)
        given(beans.topicService()).willReturn(topics)
        given(topics.getRoomByName(ByStringRequest("lobby")))
            .willReturn(Mono.just(MessageTopic.create(Key.of(ROOM, 6L), "lobby")))
        given(messages.submit(anyObject())).willReturn(Mono.just(
            MessageSendResult(Receipt("c-1", SimpleMessageKey(5L, 7L, 10L, ROOM)), CallerOutcome.COMPLETED, emptyMap())))
        given(messages.commandStatus(anyObject())).willReturn(Mono.just(CommandStatus(
            "c-1", 10L, "r-1", Receipt("c-1", SimpleMessageKey(5L, 7L, 10L, ROOM)),
            mapOf(BackendId.PERSISTENCE to BackendStatus(BackendState.SUCCEEDED, 1)), 2L)))
        given(messages.listMessages(anyObject())).willReturn(Flux.empty())
        given(users.findByUsername(ByStringRequest("nobody"))).willReturn(Flux.error(RuntimeException("denied")))
        given(users.findByUsername(ByStringRequest("ghost")))
            .willReturn(Flux.just(User.create(Key.of(11L, 8L), "Ghost", "ghost", "")))

        val pubsub = PubSubCommands(beans, TypeUtil.LongUtil, RootKeys(), ShellRooms(beans, TypeUtil.LongUtil))
        val login = LoginCommands(beans, RootKeys(), TypeUtil.LongUtil)
        // A plain mock answers null for any other service, so a command fails fast. A deep stub would answer a Mono that never ends.
        val core = mock(CoreServices::class.java) as CoreServices<Long, String, IndexSearchRequest>
        given(core.secretsStore()).willReturn(mock(SecretsStore::class.java) as SecretsStore<Long>)
        given(core.keyService()).willReturn(mock(IKeyService::class.java) as IKeyService<Long>)
        val user = UserCommands(
            core,
            beans,
            mock(AuthorizationService::class.java) as AuthorizationService<Long, AuthMetadata<Long>>,
            TypeUtil.LongUtil,
            RootKeys(),
            mock(PasswordEncoder::class.java),
        )
        val registrars = listOf<Any>(
            LoginCommandsRegistrar(login),
            PubSubCommandsRegistrar(pubsub),
            TopicCommandsRegistrar(mock(TopicCommands::class.java) as TopicCommands<Long>),
            UserCommandsRegistrar(user),
            ClaudeImportCommandsRegistrar(mock(ClaudeImportCommands::class.java) as ClaudeImportCommands<Long>),
        )
        val commands = registrars.flatMap { registrar ->
            registrar.javaClass.declaredMethods
                .filter { Command::class.java.isAssignableFrom(it.returnType) }
                .map { it.invoke(registrar) as Command }
        } + Help() + Clear() + History(mock(org.jline.reader.History::class.java))
        return CommandRegistry(commands.toSet())
    }

    private fun supported(registry: CommandRegistry): Set<String> =
        registry.commands.map { it.name }.toSet() - refused

    @Test
    fun `every command that the modes run writes nothing to System out`() {
        val registry = registry()
        val execution = CommandLineExecution(DefaultCommandParser(registry), registry)

        // The first pass starts lazy parts, such as the validator, whose log line is not command output.
        lines.values.forEach { line ->
            execution.run(line, ByteArrayInputStream(ByteArray(0)),
                java.io.PrintWriter(java.io.StringWriter()), java.io.PrintWriter(java.io.StringWriter()))
        }
        val leaks = lines.mapNotNull { (name, line) ->
            val original = System.out
            val captured = ByteArrayOutputStream()
            System.setOut(PrintStream(captured, true, Charsets.UTF_8))
            try {
                execution.run(line, ByteArrayInputStream(ByteArray(0)),
                    java.io.PrintWriter(java.io.StringWriter()), java.io.PrintWriter(java.io.StringWriter()))
            } finally {
                System.setOut(original)
            }
            captured.toString(Charsets.UTF_8).takeIf { it.isNotEmpty() }?.let { "$name: $it" }
        }

        assertThat(leaks).describedAs("text that reached System.out").isEmpty()
    }

    @Test
    fun `a failed login throws the reason and keeps the cause`() {
        registry()
        val failure = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            LoginCommands(beans, RootKeys(), TypeUtil.LongUtil).login("nobody", "secret")
        }

        assertThat(failure).hasMessage("Authentication failed: denied").hasRootCauseMessage("denied")
    }

    @Test
    fun `the command table covers every registered command`() {
        assertThat(lines.keys).containsExactlyInAnyOrderElementsOf(supported(registry()))
    }

    /**
     * The scan reads the main source as text. A direct print is an unqualified
     * `println(` or `print(`, or a `System.out` or `System.err`. A call on a
     * writer, such as `err.println(`, is not direct. The one allowed place is
     * `PubSubCommands.listen`, which streams to a terminal from a background
     * subscription, and the modes refuse it. The other is the mode
     * configuration. It wraps the process streams as the writers of a command
     * and as the diagnostics writer of the portal.
     */
    @Test
    fun `no chat-shell main source prints directly, except listen`() {
        // The one-shot runner wraps the process streams once, as the writers of its command. That is the output path itself.
        val modeWriter = Regex("""OutputStreamWriter\(System\.(?:out|err), """)
        val direct = Regex("""(?<![\w.])(?:println|print)\(|System\.(?:out|err)""")
        val hits = mutableListOf<String>()
        // Surefire runs in the module directory. The host module is its sibling.
        val roots = listOf("src/main/kotlin", "../chat-shell-host/src/main/kotlin").map { Paths.get(it) }
        roots.flatMap { root -> Files.walk(root).use { it.toList() } }.let { files ->
            files.filter { it.toString().endsWith(".kt") }.forEach { file ->
                val text = Files.readAllLines(file)
                val listen = if (file.fileName.toString() == "PubSubCommands.kt") listenLines(text) else IntRange.EMPTY
                text.forEachIndexed { index, line ->
                    val code = line.trimStart()
                    val comment = code.startsWith("*") || code.startsWith("/*") || code.startsWith("//")
                    val modeStream = file.fileName.toString() in setOf("ShellModeConfiguration.kt", "ShellCli.kt") && modeWriter.containsMatchIn(line)
                    if (!comment && direct.containsMatchIn(line) && (index + 1) !in listen && !modeStream)
                        hits += "${file.fileName}:${index + 1}: ${line.trim()}"
                }
            }
        }

        assertThat(hits).describedAs("direct prints outside listen").isEmpty()
    }

    /** The 1-based lines from `fun listen(` up to `fun hangup(`. */
    private fun listenLines(text: List<String>): IntRange {
        val start = text.indexOfFirst { it.contains("fun listen(") }
        val end = text.indexOfFirst { it.contains("fun hangup(") }
        return (start + 1)..end
    }

    companion object {
        const val ROOM = 5L
    }
}
