package com.demo.chat.shell.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.MessageImportRequest
import com.demo.chat.security.rsocket.CoreNotFound
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.nio.file.Path

/** Imports one Claude conversation through the authenticated RSocket clients. */
@Component
@Profile("shell")
class ClaudeImportCommands<T : Any>(
    private val services: CompositeServiceBeans<T, String>,
    private val login: LoginCommands<T>,
    private val parser: ClaudeExportParser,
    private val environment: Environment,
) {
    fun importConversation(file: String, conversation: String, topic: String, human: String, assistant: String): String {
        require(topic.isNotBlank() && '-' !in topic) { "Room name '$topic' cannot be empty or contain a hyphen." }
        val parsed = parser.parse(Path.of(file), conversation)
        val password = environment.getProperty("CHAT_IMPORT_ADMIN_PASSWORD")
        require(!password.isNullOrBlank()) { "CHAT_IMPORT_ADMIN_PASSWORD is required." }

        val previousUser = ShellStateConfiguration.loggedInUser
        val previousCredential = ShellStateConfiguration.loginMetadata
        try {
            login.login("Admin", password)
            val users = services.userService()
            val humanId = exactUserId(users.findByUsername(ByStringRequest(human)).collectList().block().orEmpty(), human)
            val assistantId = exactUserId(users.findByUsername(ByStringRequest(assistant)).collectList().block().orEmpty(), assistant)

            val topics = services.topicService()
            val existing = topics.getRoomByName(ByStringRequest(topic))
                .onErrorResume(CoreNotFound::class.java) { Mono.empty() }
                .block()
            require(existing == null || existing.data == topic) { "Room lookup did not return the exact name '$topic'." }
            val roomId = existing?.key?.id ?: requireNotNull(topics.addRoom(ByStringRequest(topic)).block()) {
                "Room '$topic' was not created."
            }.id

            val messages = services.messageService()
            parsed.messages.sortedBy { it.createdAt }.forEach { turn ->
                val sourceId = "${parsed.uuid}:${turn.uuid}"
                require("import:$sourceId".length <= 128) { "Message ${turn.uuid} has a source ID above 128 characters." }
                val senderId = if (turn.sender == "human") humanId else assistantId
                requireNotNull(messages.importMessage(MessageImportRequest(turn.text, senderId, roomId, turn.createdAt, sourceId)).block()) {
                    "Message ${turn.uuid} returned no import result."
                }
            }
            return "Processed ${parsed.messages.size} text turns. Skipped turns: ${parsed.skippedTurns}. " +
                "Skipped content blocks: ${parsed.skippedBlocks}. Skipped attachments: ${parsed.skippedAttachments}."
        } finally {
            ShellStateConfiguration.loggedInUser = previousUser
            ShellStateConfiguration.loginMetadata = previousCredential
        }
    }

    private fun exactUserId(found: List<com.demo.chat.domain.User<T>>, handle: String): T {
        val exact = found.filter { it.handle == handle }
        require(exact.size == 1) { "Expected one user with exact handle '$handle', found ${exact.size}." }
        return exact.single().key.id
    }
}
