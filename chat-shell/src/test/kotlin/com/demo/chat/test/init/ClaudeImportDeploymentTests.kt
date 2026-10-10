package com.demo.chat.test.init

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.security.rsocket.CoreNotFound
import com.demo.chat.shell.commands.ClaudeImportCommands
import com.demo.chat.shell.commands.LoginCommands
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
    @Autowired private lateinit var environment: ConfigurableEnvironment

    @Test
    fun `fixture imports in order and a rerun adds no message`() = withAdminPassword {
        login.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)
        val suffix = System.nanoTime().toString()
        val room = "claudeImport$suffix"
        val human = "human$suffix"
        val assistant = "assistant$suffix"
        services.userService().addUser(UserCreateRequest("Human", human, "")).block()
        services.userService().addUser(UserCreateRequest("Assistant", assistant, "")).block()
        val fixture = "src/test/resources/import/claude-conversations.json"

        val first = importer.importConversation(fixture, "Sanitized import fixture", room, human, assistant)
        assertThat(first).contains("Processed 2 text turns")
        val roomId = services.topicService().getRoomByName(ByStringRequest(room)).block()!!.key.id
        val humanId = services.userService().findByUsername(ByStringRequest(human)).collectList().block()!!.single().key.id
        val assistantId = services.userService().findByUsername(ByStringRequest(assistant)).collectList().block()!!.single().key.id
        val messages = services.messageService().listMessages(ByIdRequest(roomId))
            .collectList().block()!!.sortedBy { it.key.timestamp }
        assertThat(messages).hasSize(2)
        assertThat(messages.map { it.data }).containsExactly("First turn.", "Second\n\nturn.")
        assertThat(messages.map { it.key.from }).containsExactly(humanId, assistantId)

        importer.importConversation(fixture, "Sanitized import fixture", room, human, assistant)
        val rerunMessages = services.messageService().listMessages(ByIdRequest(roomId))
            .collectList().block()!!.sortedBy { it.key.timestamp }
        assertThat(rerunMessages.map { it.key.id }).containsExactlyElementsOf(messages.map { it.key.id })
    }

    @Test
    fun `missing user leaves no room`() = withAdminPassword {
        val suffix = System.nanoTime().toString()
        val room = "missingImport$suffix"
        val missing = "missing$suffix"
        val fixture = "src/test/resources/import/claude-conversations.json"

        assertThatThrownBy {
            importer.importConversation(fixture, "Sanitized import fixture", room, missing, "Admin")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining(missing)

        login.login(ShellDeploymentAccount.ADMIN_HANDLE, ShellDeploymentAccount.adminPassword)
        assertThatThrownBy { services.topicService().getRoomByName(ByStringRequest(room)).block() }
            .isInstanceOf(CoreNotFound::class.java)
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
