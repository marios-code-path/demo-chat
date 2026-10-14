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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

@Tag("integration")
class ClaudeImportDeploymentTests : ShellIntegrationTestBase() {
    @Autowired private lateinit var importer: ClaudeImportCommands<Long>
    @Autowired private lateinit var login: LoginCommands<Long>
    @Autowired private lateinit var services: CompositeServiceBeans<Long, String>
    @Autowired private lateinit var pubsub: PubSubCommands<Long>
    @Autowired private lateinit var environment: ConfigurableEnvironment

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
