package com.demo.chat.test.init

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.security.rsocket.CoreNotFound
import com.demo.chat.shell.commands.ClaudeImportCommands
import com.demo.chat.shell.commands.LoginCommands
import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.claudeTopicName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

// An import derives a name-based UUID message id, so this class runs the UUID image. CHAT-bwzjstoh.
@Tag("integration")
class ClaudeImportDeploymentTests : UuidShellIntegrationTestBase() {
    @Autowired private lateinit var importer: ClaudeImportCommands<UUID>
    @Autowired private lateinit var login: LoginCommands<UUID>
    @Autowired private lateinit var services: CompositeServiceBeans<UUID, String>
    @Autowired private lateinit var pubsub: PubSubCommands<UUID>
    @Autowired private lateinit var environment: ConfigurableEnvironment

    @TempDir
    lateinit var temp: Path

    @Test
    fun `fixture imports in order and a rerun adds no message`() = withAdminPassword {
        login.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)
        val suffix = System.nanoTime().toString()
        val base = "claudeImport$suffix"
        val human = "human$suffix"
        val assistant = "assistant$suffix"
        services.userService().addUser(UserCreateRequest("Human", human, "")).block()
        services.userService().addUser(UserCreateRequest("Assistant", assistant, "")).block()
        val fixture = "src/test/resources/import/claude-conversations.json"
        val room = claudeTopicName(base, CONVERSATION_UUID, "1", LEAF_UUID)

        val first = importer.importConversation(fixture, "Sanitized import fixture", base, "1", human, assistant)
        assertThat(first).contains(
            "Topics created: 1 of 1", "Messages imported: 2", "Missing parents: 1 for $LEAF_UUID")
        val roomId = services.topicService().getRoomByName(ByStringRequest(room)).block()!!.key.id
        val humanId = services.userService().findByUsername(ByStringRequest(human)).collectList().block()!!.single().key.id
        val assistantId = services.userService().findByUsername(ByStringRequest(assistant)).collectList().block()!!.single().key.id
        val messages = services.messageService().listMessages(ByIdRequest(roomId)).collectList().block()!!
        assertThat(messages).hasSize(2)
        assertThat(messages.map { it.data }).containsExactly("First turn.", "Second\n\nturn.")
        assertThat(messages.map { it.key.from }).containsExactly(humanId, assistantId)

        val shown = pubsub.messages(room)
        val lines = shown.lines()
        assertThat(lines).hasSize(4)
        assertThat(lines[0]).endsWith(" | $human | First turn.")
        assertThat(lines[1]).endsWith(" | $assistant | Second")
        assertThat(lines[2]).isBlank()
        assertThat(lines[3].trim()).isEqualTo("turn.")

        val rerun = importer.importConversation(fixture, "Sanitized import fixture", base, "1", human, assistant)
        assertThat(rerun).contains("Topics created: 0 of 1")
        val rerunMessages = services.messageService().listMessages(ByIdRequest(roomId)).collectList().block()!!
        assertThat(rerunMessages.map { it.key.id }).containsExactlyElementsOf(messages.map { it.key.id })
        assertThat(pubsub.messages(room)).isEqualTo(shown)
    }

    @Test
    fun `a second generation names a different topic`() = withAdminPassword {
        login.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)
        val suffix = System.nanoTime().toString()
        val base = "claudeGeneration$suffix"
        val human = "human$suffix"
        val assistant = "assistant$suffix"
        services.userService().addUser(UserCreateRequest("Human", human, "")).block()
        services.userService().addUser(UserCreateRequest("Assistant", assistant, "")).block()
        val fixture = "src/test/resources/import/claude-conversations.json"

        importer.importConversation(fixture, "Sanitized import fixture", base, "1", human, assistant)
        val second = importer.importConversation(fixture, "Sanitized import fixture", base, "2", human, assistant)

        assertThat(second).contains("Topics created: 1 of 1")
        val first = services.topicService()
            .getRoomByName(ByStringRequest(claudeTopicName(base, CONVERSATION_UUID, "1", LEAF_UUID))).block()!!.key.id
        val next = services.topicService()
            .getRoomByName(ByStringRequest(claudeTopicName(base, CONVERSATION_UUID, "2", LEAF_UUID))).block()!!.key.id
        assertThat(next).isNotEqualTo(first)
    }

    /**
     * Two leaves share one root record, so the route admits that record into
     * two rooms. One request ID cannot name both. The importer scopes the
     * source ID by the topic ID for this reason.
     */
    @Test
    fun `a forked conversation imports each leaf into its own topic`() = withAdminPassword {
        login.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)
        val suffix = System.nanoTime().toString()
        val base = "claudeFork$suffix"
        val human = "human$suffix"
        val assistant = "assistant$suffix"
        services.userService().addUser(UserCreateRequest("Human", human, "")).block()
        services.userService().addUser(UserCreateRequest("Assistant", assistant, "")).block()
        val file = forkFixture()

        val first = importer.importConversation(file, "Forked", base, "1", human, assistant)
        assertThat(first).contains("Topics created: 2 of 2", "Messages imported: 4", "Missing parents: 0")

        val branchRoom = claudeTopicName(base, CONVERSATION_UUID, "1", FIRST_BRANCH_UUID)
        val leafRoom = claudeTopicName(base, CONVERSATION_UUID, "1", LEAF_UUID)
        val branchId = services.topicService().getRoomByName(ByStringRequest(branchRoom)).block()!!.key.id
        val leafId = services.topicService().getRoomByName(ByStringRequest(leafRoom)).block()!!.key.id
        assertThat(branchId).isNotEqualTo(leafId)
        assertThat(storedTexts(branchId)).containsExactly("Root", "First branch")
        assertThat(storedTexts(leafId)).containsExactly("Root", "Second branch")

        val rerun = importer.importConversation(file, "Forked", base, "1", human, assistant)
        assertThat(rerun).contains("Topics created: 0 of 2", "Messages imported: 4")
        assertThat(storedTexts(branchId)).containsExactly("Root", "First branch")
        assertThat(storedTexts(leafId)).containsExactly("Root", "Second branch")
    }

    @Test
    fun `missing user leaves no room`() = withAdminPassword {
        val suffix = System.nanoTime().toString()
        val base = "missingImport$suffix"
        val missing = "missing$suffix"
        val fixture = "src/test/resources/import/claude-conversations.json"

        assertThatThrownBy {
            importer.importConversation(fixture, "Sanitized import fixture", base, "1", missing, "Admin")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining(missing)

        login.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)
        assertThatThrownBy {
            services.topicService()
                .getRoomByName(ByStringRequest(claudeTopicName(base, CONVERSATION_UUID, "1", LEAF_UUID))).block()
        }.isInstanceOf(CoreNotFound::class.java)
    }

    private companion object {
        /** The one conversation of the fixture, and its one leaf path. */
        const val CONVERSATION_UUID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val LEAF_UUID = "33333333-3333-4333-8333-333333333333"

        /** The fork fixture: one root, and one branch per leaf. */
        const val ROOT_UUID = "11111111-1111-4111-8111-111111111111"
        const val FIRST_BRANCH_UUID = "22222222-2222-4222-8222-222222222222"
    }

    private fun storedTexts(roomId: UUID): List<String> =
        services.messageService().listMessages(ByIdRequest(roomId)).collectList().block()!!.map { it.data }

    private fun forkFixture(): String {
        val file = temp.resolve("forked-conversations.json")
        Files.writeString(file, """[
          {"uuid":"$CONVERSATION_UUID","name":"Forked","chat_messages":[
            {"uuid":"$ROOT_UUID","sender":"human","created_at":"2024-01-01T10:00:00Z","text":"Root"},
            {"uuid":"$FIRST_BRANCH_UUID","sender":"assistant","created_at":"2024-01-01T10:00:01Z","text":"First branch","parent_message_uuid":"$ROOT_UUID"},
            {"uuid":"$LEAF_UUID","sender":"assistant","created_at":"2024-01-01T10:00:02Z","text":"Second branch","parent_message_uuid":"$ROOT_UUID"}
          ]}
        ]""")
        return file.toString()
    }

    private fun withAdminPassword(action: () -> Unit) {
        environment.propertySources.addFirst(MapPropertySource("claude-import-test", mapOf(
            "CHAT_IMPORT_ADMIN_PASSWORD" to ShellDeploymentAccount.adminPassword
        )))
        try {
            action()
        } finally {
            environment.propertySources.remove("claude-import-test")
        }
    }
}
