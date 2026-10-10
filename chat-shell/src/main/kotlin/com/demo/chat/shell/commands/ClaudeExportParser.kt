package com.demo.chat.shell.commands

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

data class ClaudeTurn(
    val uuid: String,
    val sender: String,
    val createdAt: Instant,
    val text: String,
)

data class ClaudeConversation(
    val uuid: String,
    val name: String,
    val messages: List<ClaudeTurn>,
    val skippedBlocks: Int,
    val skippedAttachments: Int,
    val skippedTurns: Int,
)

/** Reads text from one conversation in a Claude data export. */
@Component
@Profile("shell")
class ClaudeExportParser(private val mapper: ObjectMapper = ObjectMapper()) {
    fun parse(file: Path, selector: String): ClaudeConversation {
        val root = Files.newBufferedReader(file).use(mapper::readTree)
        require(root.isArray) { "The export must contain a conversation array." }
        val matches = root.filter { item ->
            item.field("uuid") == selector || item.field("name") == selector
        }
        require(matches.size == 1) { "Expected one conversation for '$selector', found ${matches.size}." }
        val conversation = matches.single()
        val uuid = conversation.field("uuid")
        requireUuid(uuid, "conversation")
        val turns = conversation.path("chat_messages")
        require(turns.isArray) { "Conversation $uuid has no chat_messages array." }

        val messages = mutableListOf<ClaudeTurn>()
        var skippedBlocks = 0
        var skippedAttachments = 0
        var skippedTurns = 0
        for (turn in turns) {
            val messageId = turn.field("uuid")
            requireUuid(messageId, "message")
            val sender = turn.field("sender")
            require(sender == "human" || sender == "assistant") { "Message $messageId has an unknown sender: $sender." }
            val createdAt = try {
                Instant.parse(turn.field("created_at"))
            } catch (error: Exception) {
                throw IllegalArgumentException("Message $messageId has an invalid created_at.", error)
            }
            val content = turn.get("content")
            require(content == null || content.isNull || content.isArray) { "Message $messageId has an invalid content field." }
            val text = if (content != null && content.isArray && content.size() > 0) {
                buildString {
                    for (block in content) {
                        if (block.field("type") == "text") {
                            val part = block.path("text")
                            require(part.isTextual) { "Message $messageId has an invalid text block." }
                            append(part.asText())
                        } else {
                            skippedBlocks++
                        }
                    }
                }
            } else {
                turn.path("text").takeIf { it.isTextual }?.asText().orEmpty()
            }
            skippedAttachments += turn.path("attachments").takeIf { it.isArray }?.size() ?: 0
            if (text.isBlank()) {
                skippedTurns++
            } else {
                messages += ClaudeTurn(messageId, sender, createdAt, text)
            }
        }
        return ClaudeConversation(uuid, conversation.field("name"), messages, skippedBlocks, skippedAttachments, skippedTurns)
    }

    private fun JsonNode.field(name: String): String {
        val node = path(name)
        require(node.isTextual && node.asText().isNotBlank()) { "Expected a nonempty $name field." }
        return node.asText()
    }

    private fun requireUuid(value: String, kind: String) {
        try {
            UUID.fromString(value)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid $kind UUID: $value.", error)
        }
    }
}
