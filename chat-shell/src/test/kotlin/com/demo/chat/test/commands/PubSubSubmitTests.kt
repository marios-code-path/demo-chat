package com.demo.chat.test.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.TypeUtil
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
import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.ShellRooms
import com.demo.chat.test.anyObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

/** The shell `send` submits a command with a request ID, and `command-status` reads it back. */
@Suppress("UNCHECKED_CAST")
class PubSubSubmitTests {
    private val messages = mock(ChatMessageService::class.java) as ChatMessageService<Long, String>
    private val users = mock(ChatUserService::class.java) as ChatUserService<Long>
    private val topics = mock(ChatTopicService::class.java) as ChatTopicService<Long, String>
    private val beans = mock(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>

    /** The room `lobby` resolves to [ROOM], as in `PubSubMessagesTests`. */
    private val commands: PubSubCommands<Long> by lazy {
        given(beans.messageService()).willReturn(messages)
        given(beans.userService()).willReturn(users)
        given(beans.topicService()).willReturn(topics)
        given(topics.getRoomByName(ByStringRequest("lobby")))
            .willReturn(Mono.just(MessageTopic.create(Key.of(ROOM, 6L), "lobby")))
        PubSubCommands(beans, TypeUtil.LongUtil, RootKeys(), ShellRooms(beans, TypeUtil.LongUtil))
    }

    private fun receipt(commandId: String, messageId: Long) =
        Receipt(commandId, SimpleMessageKey(messageId, 7L, 10L, ROOM))

    @Test
    fun `send keeps a supplied request ID and prints the outcome`() {
        // ArgumentCaptor.capture() answers null, and Kotlin refuses null for this parameter. The answer records the request.
        val seen = AtomicReference<MessageSubmitRequest<Long, String>>()
        given(messages.submit(anyObject())).willAnswer {
            seen.set(it.getArgument(0))
            Mono.just(MessageSendResult(receipt("c-shell", 5L), CallerOutcome.COMPLETED, emptyMap()))
        }

        val printed = captureOut { commands.send("lobby", "_", "hello", "shell-1") }

        assertThat(seen.get().requestId).isEqualTo("shell-1")
        assertThat(seen.get().dest).isEqualTo(ROOM)
        assertThat(printed).contains("Request Id: shell-1", "Message Id: 5", "Outcome: COMPLETED")
    }

    @Test
    fun `send creates a request ID when none is supplied`() {
        given(messages.submit(anyObject()))
            .willReturn(Mono.just(MessageSendResult(receipt("c-shell-2", 6L), CallerOutcome.PENDING, emptyMap())))
        val printed = captureOut { commands.send("lobby", "_", "hello", "_") }
        assertThat(printed).containsPattern("Request Id: [0-9a-f-]{36}").contains("Outcome: PENDING")
    }

    @Test
    fun `command status prints each backend`() {
        val status = CommandStatus(
            "c-shell-3", 10L, "shell-3", receipt("c-shell-3", 7L),
            mapOf(BackendId.PERSISTENCE to BackendStatus(BackendState.SUCCEEDED, 1)),
            2L,
        )
        given(messages.commandStatus(anyObject())).willReturn(Mono.just(status))
        val printed = captureOut { commands.commandStatus("c-shell-3") }
        assertThat(printed).contains("PERSISTENCE: SUCCEEDED")
    }

    private fun captureOut(block: () -> Unit): String {
        val original = System.out
        val buffer = java.io.ByteArrayOutputStream()
        System.setOut(java.io.PrintStream(buffer))
        try { block() } finally { System.setOut(original) }
        return buffer.toString()
    }

    companion object {
        const val ROOM = 5L
    }
}
