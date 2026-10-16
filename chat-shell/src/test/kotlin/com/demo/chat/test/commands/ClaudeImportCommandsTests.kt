package com.demo.chat.test.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.security.rsocket.CoreNotFound
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.shell.commands.ClaudeExportParser
import com.demo.chat.shell.commands.ClaudeImportCommands
import com.demo.chat.shell.commands.LoginCommands
import com.demo.chat.shell.commands.claudeTopicName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import org.springframework.mock.env.MockEnvironment
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * **No test here uses an argument matcher.** A Kotlin interface declares its
 * parameters non-null, so `any()` returns a null that the compiler rejects at
 * the call site. Every stub names the exact request instead, and the counts
 * are read from the recorded invocations.
 */
@Suppress("UNCHECKED_CAST")
class ClaudeImportCommandsTests {
    private val users = mock(ChatUserService::class.java) as ChatUserService<Long>
    private val topics = mock(ChatTopicService::class.java) as ChatTopicService<Long, String>
    private val messages = mock(ChatMessageService::class.java) as ChatMessageService<Long, String>
    private val beans = mock(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
    private val login = mock(LoginCommands::class.java) as LoginCommands<Long>
    /**
     * A copy of the export in [temp]. The import writes its map beside the
     * export, so a run on the resource file would write into the source tree.
     */
    private val fixture: String
        get() {
            val copy = temp.resolve("claude-conversations.json")
            if (Files.notExists(copy)) Files.copy(Path.of("src/test/resources/import/claude-conversations.json"), copy)
            return copy.toString()
        }

    /** The import map reads the message id from the result key. */
    private val result: Message<Long, String> = Message.create(MessageKey.of(100L, 7L, 11L, 5L), "", true)

    private val conversationUuid = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val rootUuid = "11111111-1111-4111-8111-111111111111"
    private val firstBranchUuid = "22222222-2222-4222-8222-222222222222"
    private val leafUuid = "33333333-3333-4333-8333-333333333333"

    @TempDir
    lateinit var temp: Path

    private fun command(): ClaudeImportCommands<Long> {
        given(beans.userService()).willReturn(users)
        given(beans.topicService()).willReturn(topics)
        given(beans.messageService()).willReturn(messages)
        return ClaudeImportCommands(beans, login, ClaudeExportParser(),
            MockEnvironment().withProperty("CHAT_IMPORT_ADMIN_PASSWORD", "test-secret"))
    }

    private fun user(id: Long, handle: String): User<Long> =
        User.create(Key.of(id, 8L), handle, handle, "")

    private fun bothUsers() {
        given(users.findByUsername(ByStringRequest("alice"))).willReturn(Flux.just(user(11L, "alice")))
        given(users.findByUsername(ByStringRequest("claude"))).willReturn(Flux.just(user(12L, "claude")))
    }

    /** A fork with a complete path. Two leaves share one root record. */
    private fun forked(): String {
        val file = temp.resolve("forked-conversations.json")
        Files.writeString(file, """[
          {"uuid":"$conversationUuid","name":"Forked","chat_messages":[
            {"uuid":"$rootUuid","sender":"human","created_at":"2024-01-01T10:00:00Z","text":"Root"},
            {"uuid":"$firstBranchUuid","sender":"assistant","created_at":"2024-01-01T10:00:01Z","text":"First branch","parent_message_uuid":"$rootUuid"},
            {"uuid":"$leafUuid","sender":"assistant","created_at":"2024-01-01T10:00:02Z","text":"Second branch","parent_message_uuid":"$rootUuid"}
          ]}
        ]""")
        return file.toString()
    }

    /** A conversation whose root carries one child per given branch UUID. */
    private fun branched(vararg branches: String): String {
        val file = temp.resolve("branched-${branches.joinToString("-")}.json")
        val children = branches.joinToString(",") { uuid ->
            """{"uuid":"$uuid","sender":"assistant","created_at":"2024-01-01T10:00:01Z","text":"Branch $uuid","parent_message_uuid":"$rootUuid"}"""
        }
        Files.writeString(file, """[
          {"uuid":"$conversationUuid","name":"Branched","chat_messages":[
            {"uuid":"$rootUuid","sender":"human","created_at":"2024-01-01T10:00:00Z","text":"Root"},
            $children
          ]}
        ]""")
        return file.toString()
    }

    private fun room(id: Long, name: String): MessageTopic<Long> =
        MessageTopic.create(Key.of(id, 6L), name)

    /** The source ID carries the room, so one record can land in two topics. */
    private fun importRequest(text: String, sender: Long, room: Long, time: String, messageUuid: String) =
        MessageImportRequest(text, sender, room, Instant.parse(time), "$room:$messageUuid")

    /** The two turns of the fixture, and the room the command resolves. */
    private fun fixtureTurns(room: Long): Pair<MessageImportRequest<Long, String>, MessageImportRequest<Long, String>> =
        importRequest("First turn.", 11L, room, "2024-01-01T10:00:00.123Z", rootUuid) to
            importRequest("Second\n\nturn.", 12L, room, "2024-01-01T10:00:01.456Z", firstBranchUuid)

    private fun roomOf(name: String, id: Long = 5L) {
        given(topics.getRoomByName(ByStringRequest(name))).willReturn(Mono.just(room(id, name)))
    }

    private fun roomIsAbsent(name: String) {
        given(topics.getRoomByName(ByStringRequest(name)))
            .willReturn(Mono.error(CoreNotFound(RuntimeException("Object not Found"))))
    }

    private fun methodCalls(target: Any): List<String> =
        mockingDetails(target).invocations.map { it.method.name }

    private fun importedRequests(): List<MessageImportRequest<Long, String>> =
        mockingDetails(messages).invocations
            .filter { it.method.name == "importMessage" }
            .map { it.arguments.single() as MessageImportRequest<Long, String> }

    @Test
    fun `missing human user stops before a room or message write`() {
        given(users.findByUsername(ByStringRequest("alice"))).willReturn(Flux.empty())

        assertThatThrownBy {
            command().importConversation(fixture, "Sanitized import fixture", "lobby", "1", "alice", "claude")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("alice")

        verifyNoInteractions(topics, messages)
    }

    @Test
    fun `case folded user lookup must return the exact handle`() {
        given(users.findByUsername(ByStringRequest("alice"))).willReturn(Flux.just(user(11L, "Alice")))

        assertThatThrownBy {
            command().importConversation(fixture, "Sanitized import fixture", "lobby", "1", "alice", "claude")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("alice")

        verifyNoInteractions(topics, messages)
    }

    @Test
    fun `a blank topic base stops before login or writes`() {
        assertThatThrownBy {
            command().importConversation(fixture, "Sanitized import fixture", " ", "1", "alice", "claude")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Topic base")

        verifyNoInteractions(login, users, topics, messages)
    }

    @Test
    fun `a generation that is not decimal stops before login or writes`() {
        assertThatThrownBy {
            command().importConversation(fixture, "Sanitized import fixture", "lobby", "two", "alice", "claude")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("decimal")

        verifyNoInteractions(login, users, topics, messages)
    }

    @Test
    fun `missing Admin password stops before login or writes`() {
        given(beans.userService()).willReturn(users)
        given(beans.topicService()).willReturn(topics)
        given(beans.messageService()).willReturn(messages)
        val command = ClaudeImportCommands(beans, login, ClaudeExportParser(), MockEnvironment())

        assertThatThrownBy {
            command.importConversation(fixture, "Sanitized import fixture", "lobby", "1", "alice", "claude")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("CHAT_IMPORT_ADMIN_PASSWORD")

        verifyNoInteractions(login, users, topics, messages)
    }

    @Test
    fun `missing assistant stops before a room or message write`() {
        given(users.findByUsername(ByStringRequest("alice"))).willReturn(Flux.just(user(11L, "alice")))
        given(users.findByUsername(ByStringRequest("claude"))).willReturn(Flux.empty())

        assertThatThrownBy {
            command().importConversation(fixture, "Sanitized import fixture", "lobby", "1", "alice", "claude")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("claude")

        verifyNoInteractions(topics, messages)
    }

    @Test
    fun `a hyphenated topic base names the room`() {
        bothUsers()
        val name = claudeTopicName("my-room", conversationUuid, "1", leafUuid)
        roomOf(name)
        val (first, second) = fixtureTurns(5L)
        given(messages.importMessage(first)).willReturn(Mono.just(result))
        given(messages.importMessage(second)).willReturn(Mono.just(result))

        command().importConversation(fixture, "Sanitized import fixture", "my-room", "1", "alice", "claude")

        verify(topics).getRoomByName(ByStringRequest("my-room:$conversationUuid:1:$leafUuid"))
        assertThat(methodCalls(topics)).doesNotContain("addRoom")
    }

    @Test
    fun `existing room receives each text turn with sender time and source ID`() {
        bothUsers()
        roomOf(claudeTopicName("lobby", conversationUuid, "1", leafUuid))
        val (first, second) = fixtureTurns(5L)
        given(messages.importMessage(first)).willReturn(Mono.just(result))
        given(messages.importMessage(second)).willReturn(Mono.just(result))

        val summary = command().importConversation(fixture, "Sanitized import fixture", "lobby", "1", "alice", "claude")

        assertThat(summary).contains(
            "Topics created: 0 of 1", "Messages imported: 2", "Missing parents: 1 for $leafUuid",
            "Skipped turns: 1", "Skipped content blocks: 3", "Skipped attachments: 1", "Skipped files: 1")
        val order = inOrder(messages)
        order.verify(messages).importMessage(first)
        order.verify(messages).importMessage(second)
        assertThat(methodCalls(topics)).doesNotContain("addRoom")
    }

    /** The four requests of the fork, with the room each one lands in. */
    private fun forkedRequests() = listOf(
        importRequest("Root", 11L, 5L, "2024-01-01T10:00:00Z", rootUuid),
        importRequest("First branch", 12L, 5L, "2024-01-01T10:00:01Z", firstBranchUuid),
        importRequest("Root", 11L, 6L, "2024-01-01T10:00:00Z", rootUuid),
        importRequest("Second branch", 12L, 6L, "2024-01-01T10:00:02Z", leafUuid),
    )

    private fun stubFork() {
        forkedRequests().forEach { given(messages.importMessage(it)).willReturn(Mono.just(result)) }
    }

    private fun forkNames() = claudeTopicName("lobby", conversationUuid, "7", firstBranchUuid) to
        claudeTopicName("lobby", conversationUuid, "7", leafUuid)

    @Test
    fun `a forked conversation receives one topic per leaf`() {
        bothUsers()
        val (first, second) = forkNames()
        roomIsAbsent(first)
        roomIsAbsent(second)
        given(topics.addRoom(ByStringRequest(first))).willReturn(Mono.just(Key.of(5L, 6L)))
        given(topics.addRoom(ByStringRequest(second))).willReturn(Mono.just(Key.of(6L, 6L)))
        stubFork()

        val created = command().importConversation(forked(), "Forked", "lobby", "7", "alice", "claude")

        assertThat(created).contains("Topics created: 2 of 2", "Messages imported: 4", "Missing parents: 0")
        verify(topics, times(1)).addRoom(ByStringRequest(first))
        verify(topics, times(1)).addRoom(ByStringRequest(second))
    }

    @Test
    fun `a forked rerun creates no second topic`() {
        bothUsers()
        val (first, second) = forkNames()
        roomOf(first, 5L)
        roomOf(second, 6L)
        stubFork()

        val rerun = command().importConversation(forked(), "Forked", "lobby", "7", "alice", "claude")

        assertThat(rerun).contains("Topics created: 0 of 2", "Messages imported: 4")
        assertThat(methodCalls(topics)).doesNotContain("addRoom")
    }

    @Test
    fun `a thread holds its own records and the shared root alone`() {
        bothUsers()
        val (first, second) = forkNames()
        roomOf(first, 5L)
        roomOf(second, 6L)
        stubFork()

        command().importConversation(forked(), "Forked", "lobby", "7", "alice", "claude")

        val imported = importedRequests()
        assertThat(imported).hasSize(4)
        assertThat(imported.filter { it.dest == 5L }.map { it.msg }).containsExactly("Root", "First branch")
        assertThat(imported.filter { it.dest == 6L }.map { it.msg }).containsExactly("Root", "Second branch")
    }

    /**
     * The route keys a request on the owner and the request ID, and it treats
     * a differing room as a conflict. So one source ID cannot name the shared
     * parent of two topics. The topic ID scopes the source ID.
     */
    @Test
    fun `a shared root names one source ID per topic`() {
        bothUsers()
        val (first, second) = forkNames()
        roomOf(first, 5L)
        roomOf(second, 6L)
        stubFork()

        command().importConversation(forked(), "Forked", "lobby", "7", "alice", "claude")

        val roots = importedRequests().filter { it.msg == "Root" }
        assertThat(roots).hasSize(2)
        assertThat(roots.map { it.messageId }).containsExactlyInAnyOrder("5:$rootUuid", "6:$rootUuid")
    }

    private fun stubMessages(request: MessageImportRequest<Long, String>) {
        given(messages.importMessage(request)).willReturn(Mono.just(result))
    }

    /**
     * A name carries the leaf UUID alone, so a second leaf adds a name. It
     * never changes the name of a topic that exists.
     */
    @Test
    fun `an added leaf renames no existing topic`() {
        bothUsers()
        val firstName = claudeTopicName("lobby", conversationUuid, "1", firstBranchUuid)
        val secondName = claudeTopicName("lobby", conversationUuid, "1", leafUuid)

        roomIsAbsent(firstName)
        given(topics.addRoom(ByStringRequest(firstName))).willReturn(Mono.just(Key.of(5L, 6L)))
        stubMessages(importRequest("Root", 11L, 5L, "2024-01-01T10:00:00Z", rootUuid))
        stubMessages(importRequest("Branch $firstBranchUuid", 12L, 5L, "2024-01-01T10:00:01Z", firstBranchUuid))

        command().importConversation(branched(firstBranchUuid), "Branched", "lobby", "1", "alice", "claude")

        roomOf(firstName, 5L)
        roomIsAbsent(secondName)
        given(topics.addRoom(ByStringRequest(secondName))).willReturn(Mono.just(Key.of(6L, 6L)))
        stubMessages(importRequest("Root", 11L, 6L, "2024-01-01T10:00:00Z", rootUuid))
        stubMessages(importRequest("Branch $leafUuid", 12L, 6L, "2024-01-01T10:00:01Z", leafUuid))

        val added = command()
            .importConversation(branched(firstBranchUuid, leafUuid), "Branched", "lobby", "1", "alice", "claude")

        assertThat(added).contains("Topics created: 1 of 2")
        // The first import made the first topic, and the second import made
        // the second one alone. So the second import found the first topic.
        verify(topics, times(1)).addRoom(ByStringRequest(firstName))
        verify(topics, times(1)).addRoom(ByStringRequest(secondName))
    }

    /** The route limits one request ID to 128 characters. */
    @Test
    fun `the largest room id fits the request limit`() {
        bothUsers()
        val roomId = Long.MAX_VALUE
        roomOf(claudeTopicName("lobby", conversationUuid, "1", leafUuid), roomId)
        val (first, second) = fixtureTurns(roomId)
        stubMessages(first)
        stubMessages(second)

        command().importConversation(fixture, "Sanitized import fixture", "lobby", "1", "alice", "claude")

        val requests = importedRequests()
        assertThat(requests).hasSize(2)
        assertThat(requests.maxOf { "import:${it.messageId}".length }).isLessThanOrEqualTo(128)
    }

    @Test
    fun `the import never creates a pointer`() {
        bothUsers()
        roomOf(claudeTopicName("lobby", conversationUuid, "1", leafUuid))
        val (first, second) = fixtureTurns(5L)
        given(messages.importMessage(first)).willReturn(Mono.just(result))
        given(messages.importMessage(second)).willReturn(Mono.just(result))

        command().importConversation(fixture, "Sanitized import fixture", "lobby", "1", "alice", "claude")

        assertThat(methodCalls(messages)).doesNotContain("pointer")
    }
}