package com.demo.chat.test.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.shell.cli.LaunchDeadline
import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus
import com.demo.chat.shell.cli.StreamInput
import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.PubSubCommandsRegistrar
import com.demo.chat.shell.commands.ShellRooms
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.shell.core.InputReader
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.DefaultCommandParser
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import java.time.Instant

/**
 * `register-read` is the session-start read of the topic register: the whole
 * index, then the newest entry of each topic that has one younger than 24
 * hours. It runs in one process, so one JVM start pays for every read. It
 * prints only after every read succeeds, so a failure leaves no partial
 * register. See CHAT-jmnjvupr and owner decision 20.
 */
@Suppress("UNCHECKED_CAST")
class PubSubRegisterReadTests {
    private val messages = mock(ChatMessageService::class.java) as ChatMessageService<Long, String>
    private val users = mock(ChatUserService::class.java) as ChatUserService<Long>
    private val topics = mock(ChatTopicService::class.java) as ChatTopicService<Long, String>
    private val beans = mock(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val mapper = ObjectMapper()

    private fun commands(): PubSubCommands<Long> {
        given(beans.messageService()).willReturn(messages)
        given(beans.userService()).willReturn(users)
        given(beans.topicService()).willReturn(topics)
        given(users.findByUserId(ByIdRequest(11L))).willReturn(Mono.just(User.create(Key.of(11L, 8L), "Admin", "Admin", "")))
        return PubSubCommands(beans, TypeUtil.LongUtil, RootKeys(), ShellRooms(beans, TypeUtil.LongUtil))
    }

    private fun room(name: String, id: Long) {
        given(topics.getRoomByName(ByStringRequest(name))).willReturn(Mono.just(MessageTopic.create(Key.of(id, 6L), name)))
    }

    private fun stored(room: Long, vararg entries: Triple<Long, Duration, String>) {
        given(messages.listMessages(ByIdRequest(room))).willReturn(Flux.fromIterable(entries.map { (id, age, text) ->
            Message.create(SimpleMessageKey(id, 7L, 11L, room, now.minus(age)), text, true) as Message<Long, String>
        }))
    }

    private fun hours(h: Long) = Duration.ofHours(h)

    private fun read(command: PubSubCommands<Long>, limit: Duration = Duration.ofSeconds(30)): List<Map<String, String>> =
        command.registerRead("register-index", hours(24), limit, now).lines().filter { it.isNotEmpty() }
            .map { line -> mapper.readTree(line).fields().asSequence().associate { it.key to it.value.asText() } }

    @Test
    fun `the read prints the index oldest first, then the newest recent entry of each topic`() {
        room("register-index", 100)
        room("topic-a", 101)
        room("topic-b", 102)
        room("topic-c", 103)
        stored(100,
            Triple(3, hours(1), "- | topic-a | archive | duplicate topic"),
            Triple(1, hours(5), "CHAT-1 | topic-a | task | A"),
            Triple(2, hours(4), "CHAT-2 | topic-b | task | B"),
            Triple(4, hours(2), "CHAT-3 | topic-c | task | C"))
        stored(101, Triple(10, hours(30), "a old"), Triple(11, hours(1), "a new"))
        stored(102, Triple(20, hours(25), "b stale, excluded"))
        stored(103, Triple(30, hours(3), "c older"), Triple(31, hours(2), "c newest"))

        val records = read(commands())

        assertThat(records.map { it["topic"] }).containsExactly(
            "register-index", "register-index", "register-index", "register-index", "topic-a", "topic-c")
        assertThat(records.map { it["text"] }.take(4)).containsExactly(
            "CHAT-1 | topic-a | task | A", "CHAT-2 | topic-b | task | B", "CHAT-3 | topic-c | task | C",
            "- | topic-a | archive | duplicate topic")
        assertThat(records.drop(4).map { it["text"] }).containsExactly("a new", "c newest")
        assertThat(records.first()["sender"]).isEqualTo("Admin")
    }

    @Test
    fun `an entry exactly 24 hours old is not recent`() {
        room("register-index", 100)
        room("topic-a", 101)
        stored(100, Triple(1, hours(1), "CHAT-1 | topic-a | task | A"))
        stored(101, Triple(10, hours(24), "exactly a day"))

        assertThat(read(commands()).map { it["topic"] }).containsExactly("register-index")
    }

    @Test
    fun `an empty register prints nothing`() {
        room("register-index", 100)
        stored(100)

        assertThat(commands().registerRead("register-index", hours(24), Duration.ofSeconds(30), now)).isEmpty()
    }

    @Test
    fun `a pointer that does not follow the form fails the read and names the pointer`() {
        room("register-index", 100)
        stored(100, Triple(1, hours(1), "pointer without the form"))

        assertThatThrownBy { commands().registerRead("register-index", hours(24), Duration.ofSeconds(30), now) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("pointer without the form")
    }

    @Test
    fun `a topic that cannot be read fails the whole read`() {
        room("register-index", 100)
        given(topics.getRoomByName(ByStringRequest("topic-a"))).willReturn(Mono.error(IllegalStateException("store down")))
        stored(100, Triple(1, hours(1), "CHAT-1 | topic-a | task | A"))

        assertThatThrownBy { commands().registerRead("register-index", hours(24), Duration.ofSeconds(30), now) }
            .hasMessageContaining("store down")
    }

    @Test
    fun `a read past its limit gives TIMEOUT`() {
        room("register-index", 100)
        given(messages.listMessages(ByIdRequest(100L))).willReturn(Flux.never())

        assertThatThrownBy { commands().registerRead("register-index", hours(24), Duration.ofMillis(300), now) }
            .isInstanceOfSatisfying(ShellExit::class.java) {
                assertThat(it.status).isEqualTo(ShellExitStatus.TIMEOUT)
                assertThat(it.message).isEqualTo("register-read passed its 300 ms limit")
            }
    }

    @Test
    fun `the command prints each record on a line and prints nothing for an empty register`() {
        room("register-index", 100)
        stored(100)
        val registered = PubSubCommandsRegistrar(commands()).registerReadCommand()
        val registry = CommandRegistry(setOf(registered))
        val out = StringWriter()

        registered.execute(CommandContext(DefaultCommandParser(registry).parse("register-read"),
            registry, PrintWriter(out), mock(InputReader::class.java)))

        assertThat(out.toString()).isEmpty()
    }

    @AfterEach
    fun disarm() {
        LaunchDeadline.active = null
    }

    /** The shell modes give a StreamInput, so the limit counts from the launch of the process. */
    private fun runInMode(): Pair<Throwable?, String> {
        val registered = PubSubCommandsRegistrar(commands()).registerReadCommand()
        val registry = CommandRegistry(setOf(registered))
        val out = StringWriter()
        val failure = try {
            registered.execute(CommandContext(DefaultCommandParser(registry).parse("register-read"),
                registry, PrintWriter(out), StreamInput(java.io.ByteArrayInputStream(ByteArray(0)))))
            null
        } catch (e: Throwable) {
            e
        }
        return failure to out.toString()
    }

    /** Startup used the whole budget: the read must not run, and it must not succeed. */
    @Test
    fun `in a mode, a budget that startup spent gives TIMEOUT, reads nothing, and prints nothing`() {
        room("register-index", 100)
        stored(100, Triple(1, hours(1), "CHAT-1 | topic-a | task | A"))
        LaunchDeadline.active = LaunchDeadline("register-read", Instant.now().minusSeconds(31), Duration.ofSeconds(30))

        val (failure, printed) = runInMode()

        assertThat(failure).isInstanceOfSatisfying(ShellExit::class.java) { assertThat(it.status).isEqualTo(ShellExitStatus.TIMEOUT) }
        assertThat(printed).isEmpty()
        verifyNoInteractions(messages)
    }

    /** The read itself used the last of the budget: the output is refused. */
    @Test
    fun `in a mode, a budget that ran out during the read prints nothing`() {
        room("register-index", 100)
        stored(100, Triple(1, hours(1), "CHAT-1 | topic-a | task | A"))
        room("topic-a", 101)
        given(messages.listMessages(ByIdRequest(101L))).willAnswer {
            Thread.sleep(400)
            Flux.empty<Message<Long, String>>()
        }
        LaunchDeadline.active = LaunchDeadline("register-read", Instant.now().minusMillis(29_800), Duration.ofSeconds(30))

        val (failure, printed) = runInMode()

        assertThat(failure).isInstanceOfSatisfying(ShellExit::class.java) { assertThat(it.status).isEqualTo(ShellExitStatus.TIMEOUT) }
        assertThat(printed).isEmpty()
    }

    @Test
    fun `in a mode, a budget with time left prints the register`() {
        room("register-index", 100)
        stored(100, Triple(1, hours(1), "CHAT-1 | topic-a | task | A"))
        room("topic-a", 101)
        stored(101, Triple(10, hours(1), "a new"))
        LaunchDeadline.active = LaunchDeadline("register-read", Instant.now().minusSeconds(4), Duration.ofSeconds(30))

        val (failure, printed) = runInMode()

        assertThat(failure).isNull()
        assertThat(printed.lines().filter { it.isNotEmpty() }).hasSize(2)
    }

    /** The read phase times out at the same moment as the watchdog. The line must be written once. */
    @Test
    fun `in a mode, a read that times out with a watchdog armed writes one line and halts`() {
        room("register-index", 100)
        given(messages.listMessages(ByIdRequest(100L))).willReturn(Flux.never())
        val halts = java.util.concurrent.atomic.AtomicInteger()
        val err = StringWriter()
        val armed = LaunchDeadline("register-read", Instant.now().minusMillis(29_000), Duration.ofSeconds(30))
        armed.startWatchdog(PrintWriter(err)) { halts.incrementAndGet() }
        LaunchDeadline.active = armed

        val (failure, printed) = runInMode()
        Thread.sleep(300)

        assertThat(failure).isInstanceOfSatisfying(ShellExit::class.java) { assertThat(it.status).isEqualTo(ShellExitStatus.TIMEOUT) }
        assertThat(printed).isEmpty()
        assertThat(halts.get()).isGreaterThan(0)
        assertThat(err.toString().lines().filter { it.isNotEmpty() }).hasSize(1)
    }
}
