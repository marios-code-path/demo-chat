package com.demo.chat.test.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.MessageSendResult
import com.demo.chat.domain.command.Receipt
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.shell.cli.StreamInput
import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.PubSubCommandsRegistrar
import com.demo.chat.shell.commands.ShellRooms
import com.demo.chat.test.anyObject
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.shell.core.InputReader
import org.springframework.shell.core.command.Command
import org.springframework.shell.core.command.CommandContext
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.DefaultCommandParser
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicReference

/**
 * `post` sends the text of its input to a topic and answers one JSON line.
 *
 * An entry travels as command input, so its newlines need no quotes. The text
 * must arrive byte for byte: no final newline, a `\r\n` line end, and non-ASCII
 * text all stay as sent. See CHAT-jmnjvupr.
 */
@Suppress("UNCHECKED_CAST")
class PubSubPostTests {
    private val messages = mock(ChatMessageService::class.java) as ChatMessageService<Long, String>
    private val users = mock(ChatUserService::class.java) as ChatUserService<Long>
    private val topics = mock(ChatTopicService::class.java) as ChatTopicService<Long, String>
    private val beans = mock(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
    private val seen = AtomicReference<MessageSubmitRequest<Long, String>>()

    private val post: Command by lazy {
        given(beans.messageService()).willReturn(messages)
        given(beans.userService()).willReturn(users)
        given(beans.topicService()).willReturn(topics)
        given(topics.getRoomByName(ByStringRequest("lobby")))
            .willReturn(Mono.just(MessageTopic.create(Key.of(ROOM, 6L), "lobby")))
        given(messages.submit(anyObject())).willAnswer {
            seen.set(it.getArgument(0))
            Mono.just(MessageSendResult(
                Receipt("c-post", SimpleMessageKey(5L, 7L, 10L, ROOM)), CallerOutcome.COMPLETED, emptyMap()))
        }
        val commands = PubSubCommands(beans, TypeUtil.LongUtil, RootKeys(), ShellRooms(beans, TypeUtil.LongUtil))
        PubSubCommandsRegistrar(commands).postCommand()
    }

    /** Runs the command as a mode does: through the parser, with [input] as the input reader. */
    private fun run(line: String, input: InputReader): String {
        val registry = CommandRegistry(setOf(post))
        val out = StringWriter()
        val context = CommandContext(DefaultCommandParser(registry).parse(line), registry, PrintWriter(out), input)
        post.execute(context)
        return out.toString()
    }

    private fun stream(text: String): InputReader = StreamInput(ByteArrayInputStream(text.toByteArray()))

    @Test
    fun `post sends the input text byte for byte`() {
        for (text in listOf("a\nb\n", "a\r\nb", "no newline", "ünïcode ✓\n")) {
            run("post lobby", stream(text))

            assertThat(seen.get().msg).describedAs(text).isEqualTo(text)
            assertThat(seen.get().dest).isEqualTo(ROOM)
        }
    }

    @Test
    fun `post refuses empty input with USAGE`() {
        assertThatThrownBy { run("post lobby", stream("")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("post needs the entry text on standard input.")
    }

    @Test
    fun `post answers one JSON line with the four fields`() {
        val answer = run("post lobby --requestId shell-1", stream("hello"))

        assertThat(answer.lines().filter { it.isNotEmpty() }).hasSize(1)
        val json = ObjectMapper().readTree(answer)
        assertThat(json.fieldNames().asSequence().toList())
            .containsExactly("messageId", "commandId", "requestId", "outcome")
        assertThat(json["messageId"].asText()).isEqualTo("5")
        assertThat(json["commandId"].asText()).isEqualTo("c-post")
        assertThat(json["requestId"].asText()).isEqualTo("shell-1")
        assertThat(json["outcome"].asText()).isEqualTo("COMPLETED")
        assertThat(seen.get().requestId).isEqualTo("shell-1")
    }

    @Test
    fun `post outside the modes is refused`() {
        assertThatThrownBy { run("post lobby", mock(InputReader::class.java)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("post reads its text from standard input. Use it in one-shot or resident mode.")
    }

    companion object {
        const val ROOM = 5L
    }
}
