package com.demo.chat.test.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.shell.commands.ClaudeExportParser
import com.demo.chat.shell.commands.ClaudeImportCommands
import com.demo.chat.shell.commands.ClaudeImportMap
import com.demo.chat.shell.commands.LoginCommands
import com.demo.chat.shell.commands.claudeTopicName
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.mock.env.MockEnvironment
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * The source-to-chat map of `CHAT-zxrgxdlx`, task 6. A derived message id is
 * one way, so the map keeps the reverse direction.
 *
 * Each stub names the exact request, as in [ClaudeImportCommandsTests]. Each
 * import result carries its own message id, so a map entry names the message
 * that its request produced.
 */
@Suppress("UNCHECKED_CAST")
class ClaudeImportMapTests {
    private val users = mock(ChatUserService::class.java) as ChatUserService<Long>
    private val topics = mock(ChatTopicService::class.java) as ChatTopicService<Long, String>
    private val messages = mock(ChatMessageService::class.java) as ChatMessageService<Long, String>
    private val beans = mock(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
    private val login = mock(LoginCommands::class.java) as LoginCommands<Long>
    private val mapper = ObjectMapper()

    private val conversationUuid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val rootUuid = "11111111-1111-4111-8111-111111111111"
    private val firstBranchUuid = "22222222-2222-4222-8222-222222222222"
    private val leafUuid = "33333333-3333-4333-8333-333333333333"
    private val firstName = claudeTopicName("lobby", conversationUuid, "7", firstBranchUuid)
    private val secondName = claudeTopicName("lobby", conversationUuid, "7", leafUuid)

    @TempDir
    lateinit var temp: Path

    private fun command(): ClaudeImportCommands<Long> {
        given(beans.userService()).willReturn(users)
        given(beans.topicService()).willReturn(topics)
        given(beans.messageService()).willReturn(messages)
        given(users.findByUsername(ByStringRequest("alice")))
            .willReturn(Flux.just(User.create(Key.of(11L, 8L), "alice", "alice", "")))
        given(users.findByUsername(ByStringRequest("claude")))
            .willReturn(Flux.just(User.create(Key.of(12L, 8L), "claude", "claude", "")))
        return ClaudeImportCommands(beans, login, ClaudeExportParser(),
            MockEnvironment().withProperty("CHAT_IMPORT_ADMIN_PASSWORD", "test-secret"))
    }

    /** A fork with a complete path. Two leaves share one root record. */
    private fun forked(): Path {
        val file = temp.resolve("forked-conversations.json")
        Files.writeString(file, """[
          {"uuid":"$conversationUuid","name":"Forked","chat_messages":[
            {"uuid":"$rootUuid","sender":"human","created_at":"2024-01-01T10:00:00Z","text":"Root"},
            {"uuid":"$firstBranchUuid","sender":"assistant","created_at":"2024-01-01T10:00:01Z","text":"First branch","parent_message_uuid":"$rootUuid"},
            {"uuid":"$leafUuid","sender":"assistant","created_at":"2024-01-01T10:00:02Z","text":"Second branch","parent_message_uuid":"$rootUuid"}
          ]}
        ]""")
        return file
    }

    private fun mapPath() = temp.resolve("claude-import-map-$conversationUuid-7.json")

    /** The four requests of the fork, each with the message id that its import returns. */
    private fun stubFork() {
        listOf(
            ForkTurn("Root", 11L, 5L, "00", rootUuid, 105L),
            ForkTurn("First branch", 12L, 5L, "01", firstBranchUuid, 106L),
            ForkTurn("Root", 11L, 6L, "00", rootUuid, 107L),
            ForkTurn("Second branch", 12L, 6L, "02", leafUuid, 108L),
        ).forEach { turn ->
            val request = MessageImportRequest(turn.text, turn.sender, turn.room,
                Instant.parse("2024-01-01T10:00:${turn.second}Z"), "${turn.room}:${turn.uuid}")
            given(messages.importMessage(request)).willReturn(
                Mono.just(Message.create(MessageKey.of(turn.messageId, 7L, turn.sender, turn.room), turn.text, true)))
        }
    }

    private data class ForkTurn(
        val text: String, val sender: Long, val room: Long, val second: String, val uuid: String, val messageId: Long,
    )

    private fun roomsExist() {
        given(topics.getRoomByName(ByStringRequest(firstName))).willReturn(Mono.just(MessageTopic.create(Key.of(5L, 6L), firstName)))
        given(topics.getRoomByName(ByStringRequest(secondName))).willReturn(Mono.just(MessageTopic.create(Key.of(6L, 6L), secondName)))
    }

    private fun importCount(): Int =
        mockingDetails(messages).invocations.count { it.method.name == "importMessage" }

    @Test
    fun `the map is written beside the export`() {
        roomsExist()
        stubFork()
        val export = forked()

        val summary = command().importConversation(export.toString(), "Forked", "lobby", "7", "alice", "claude")

        assertThat(mapPath()).exists()
        assertThat(summary).contains("Map: ${mapPath()}")
        val root = mapper.readTree(mapPath().toFile())
        assertThat(root.path("conversation").asText()).isEqualTo(conversationUuid)
        assertThat(root.path("generation").asText()).isEqualTo("7")
        assertThat(root.path("export").asText()).isEqualTo(export.toAbsolutePath().toString())
        assertThat(root.path("topics").path(firstBranchUuid).path("topic").asText()).isEqualTo(firstName)
        assertThat(root.path("topics").path(firstBranchUuid).path("roomId").asText()).isEqualTo("5")
        assertThat(root.path("topics").path(leafUuid).path("topic").asText()).isEqualTo(secondName)
        assertThat(root.path("topics").path(leafUuid).path("roomId").asText()).isEqualTo("6")
        val second = root.path("messages").path("6:$leafUuid")
        assertThat(second.path("messageId").asText()).isEqualTo("108")
        assertThat(second.path("roomId").asText()).isEqualTo("6")
        assertThat(second.path("topic").asText()).isEqualTo(secondName)
        assertThat(root.path("messages").size()).isEqualTo(4)
    }

    /** A shared root lands in two rooms. Each room-scoped source id names its own message. */
    @Test
    fun `a reverse lookup resolves each source id of a fork to its message id`() {
        roomsExist()
        stubFork()

        command().importConversation(forked().toString(), "Forked", "lobby", "7", "alice", "claude")

        val map = ClaudeImportMap.read(mapPath())
        assertThat(map.messageId("5:$rootUuid")).isEqualTo("105")
        assertThat(map.messageId("5:$firstBranchUuid")).isEqualTo("106")
        assertThat(map.messageId("6:$rootUuid")).isEqualTo("107")
        assertThat(map.messageId("6:$leafUuid")).isEqualTo("108")
        assertThat(map.messageId("7:$rootUuid")).isNull()
    }

    @Test
    fun `a rerun merges into the map and does not replace it`() {
        Files.writeString(mapPath(), """{
          "conversation":"$conversationUuid","generation":"7","export":"/earlier/export.json",
          "topics":{"earlier-leaf":{"topic":"lobby:earlier","roomId":"4"}},
          "messages":{"4:earlier-turn":{"messageId":"104","roomId":"4","topic":"lobby:earlier"}}
        }""")
        roomsExist()
        stubFork()

        command().importConversation(forked().toString(), "Forked", "lobby", "7", "alice", "claude")

        val map = ClaudeImportMap.read(mapPath())
        assertThat(map.messageId("4:earlier-turn")).isEqualTo("104")
        assertThat(map.messageId("6:$leafUuid")).isEqualTo("108")
        assertThat(map.messages).hasSize(5)
        assertThat(map.topics.keys).containsExactlyInAnyOrder("earlier-leaf", firstBranchUuid, leafUuid)
    }

    @Test
    fun `a second run of the same export keeps one entry per source id`() {
        roomsExist()
        stubFork()

        command().importConversation(forked().toString(), "Forked", "lobby", "7", "alice", "claude")
        command().importConversation(forked().toString(), "Forked", "lobby", "7", "alice", "claude")

        val map = ClaudeImportMap.read(mapPath())
        assertThat(map.messages).hasSize(4)
        assertThat(map.messageId("6:$rootUuid")).isEqualTo("107")
    }

    /** The map is written after each thread, so a failed write stops the import at that thread. */
    @Test
    fun `a write failure fails the run`() {
        roomsExist()
        stubFork()
        val export = forked()
        temp.toFile().setWritable(false)
        try {
            assertThatThrownBy {
                command().importConversation(export.toString(), "Forked", "lobby", "7", "alice", "claude")
            }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining(mapPath().toString())
        } finally {
            temp.toFile().setWritable(true)
        }

        assertThat(importCount()).isEqualTo(2)
        assertThat(mapPath()).doesNotExist()
    }

    /** A map that cannot be read would lose its entries on the next write. */
    @Test
    fun `an unreadable map stops the run before any write`() {
        Files.writeString(mapPath(), "not json")
        roomsExist()
        stubFork()

        assertThatThrownBy {
            command().importConversation(forked().toString(), "Forked", "lobby", "7", "alice", "claude")
        }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining(mapPath().toString())

        verifyNoInteractions(login, topics, messages)
        assertThat(Files.readString(mapPath())).isEqualTo("not json")
    }

    @Test
    fun `a map of another conversation stops the run before any write`() {
        Files.writeString(mapPath(), """{"conversation":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","generation":"7",
          "export":"/x.json","topics":{},"messages":{}}""")

        assertThatThrownBy {
            command().importConversation(forked().toString(), "Forked", "lobby", "7", "alice", "claude")
        }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")

        verifyNoInteractions(login, topics, messages)
    }
}
