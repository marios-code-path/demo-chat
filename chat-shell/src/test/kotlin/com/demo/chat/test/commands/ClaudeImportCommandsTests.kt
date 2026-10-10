package com.demo.chat.test.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.shell.commands.ClaudeExportParser
import com.demo.chat.shell.commands.ClaudeImportCommands
import com.demo.chat.shell.commands.LoginCommands
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import org.springframework.mock.env.MockEnvironment
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.file.Path
import java.time.Instant

@Suppress("UNCHECKED_CAST")
class ClaudeImportCommandsTests {
    private val users = mock(ChatUserService::class.java) as ChatUserService<Long>
    private val topics = mock(ChatTopicService::class.java) as ChatTopicService<Long, String>
    private val messages = mock(ChatMessageService::class.java) as ChatMessageService<Long, String>
    private val beans = mock(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
    private val login = mock(LoginCommands::class.java) as LoginCommands<Long>
    private val fixture = Path.of("src/test/resources/import/claude-conversations.json").toString()

    private fun command(): ClaudeImportCommands<Long> {
        given(beans.userService()).willReturn(users)
        given(beans.topicService()).willReturn(topics)
        given(beans.messageService()).willReturn(messages)
        return ClaudeImportCommands(beans, login, ClaudeExportParser(),
            MockEnvironment().withProperty("CHAT_IMPORT_ADMIN_PASSWORD", "test-secret"))
    }

    private fun user(id: Long, handle: String): User<Long> =
        User.create(Key.of(id, 8L), handle, handle, "")

    @Test
    fun `missing human user stops before a room or message write`() {
        given(users.findByUsername(ByStringRequest("alice"))).willReturn(Flux.empty())

        assertThatThrownBy {
            command().importConversation(fixture, "Sanitized import fixture", "lobby", "alice", "claude")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("alice")

        verifyNoInteractions(topics, messages)
    }

    @Test
    fun `case folded user lookup must return the exact handle`() {
        given(users.findByUsername(ByStringRequest("alice"))).willReturn(Flux.just(user(11L, "Alice")))

        assertThatThrownBy {
            command().importConversation(fixture, "Sanitized import fixture", "lobby", "alice", "claude")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("alice")

        verifyNoInteractions(topics, messages)
    }

    @Test
    fun `hyphenated room name stops before login or writes`() {
        assertThatThrownBy {
            command().importConversation(fixture, "Sanitized import fixture", "my-room", "alice", "claude")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("my-room")

        verifyNoInteractions(login, users, topics, messages)
    }

    @Test
    fun `missing Admin password stops before login or writes`() {
        given(beans.userService()).willReturn(users)
        given(beans.topicService()).willReturn(topics)
        given(beans.messageService()).willReturn(messages)
        val command = ClaudeImportCommands(beans, login, ClaudeExportParser(), MockEnvironment())

        assertThatThrownBy {
            command.importConversation(fixture, "Sanitized import fixture", "lobby", "alice", "claude")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("CHAT_IMPORT_ADMIN_PASSWORD")

        verifyNoInteractions(login, users, topics, messages)
    }

    @Test
    fun `missing assistant stops before a room or message write`() {
        given(users.findByUsername(ByStringRequest("alice"))).willReturn(Flux.just(user(11L, "alice")))
        given(users.findByUsername(ByStringRequest("claude"))).willReturn(Flux.empty())

        assertThatThrownBy {
            command().importConversation(fixture, "Sanitized import fixture", "lobby", "alice", "claude")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("claude")

        verifyNoInteractions(topics, messages)
    }

    @Test
    fun `existing room receives each text turn with sender time and source ID`() {
        given(users.findByUsername(ByStringRequest("alice"))).willReturn(Flux.just(user(11L, "alice")))
        given(users.findByUsername(ByStringRequest("claude"))).willReturn(Flux.just(user(12L, "claude")))
        given(topics.getRoomByName(ByStringRequest("lobby")))
            .willReturn(Mono.just(MessageTopic.create(Key.of(5L, 6L), "lobby")))
        val first = MessageImportRequest("First turn.", 11L, 5L,
            Instant.parse("2024-01-01T10:00:00.123Z"),
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa:11111111-1111-4111-8111-111111111111")
        val second = MessageImportRequest("Second turn.", 12L, 5L,
            Instant.parse("2024-01-01T10:00:01.456Z"),
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa:22222222-2222-4222-8222-222222222222")
        val result = mock(Message::class.java) as Message<Long, String>
        given(messages.importMessage(first)).willReturn(Mono.just(result))
        given(messages.importMessage(second)).willReturn(Mono.just(result))

        val summary = command().importConversation(fixture, "Sanitized import fixture", "lobby", "alice", "claude")

        assertThat(summary).contains(
            "Processed 2 text turns", "Skipped turns: 1", "Skipped content blocks: 2", "Skipped attachments: 1")
        val order = inOrder(messages)
        order.verify(messages).importMessage(first)
        order.verify(messages).importMessage(second)
        verify(topics, never()).addRoom(ByStringRequest("lobby"))
    }
}
