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

/**
 * One leaf path of a conversation. [messages] runs from the path root to
 * [leafUuid]. A record that two paths share appears in both of them.
 *
 * [pathEndsAtOmittedParent] is true when the path root named a parent that
 * the export does not hold.
 */
data class ClaudeThread(
    val leafUuid: String,
    val messages: List<ClaudeTurn>,
    val pathEndsAtOmittedParent: Boolean,
)

data class ClaudeConversation(
    val uuid: String,
    val name: String,
    val threads: List<ClaudeThread>,
    val skippedBlocks: Int,
    val skippedAttachments: Int,
    val skippedTurns: Int,
    val skippedFiles: Int,
)

private data class ClaudeSourceTurn(
    val node: JsonNode,
    val uuid: String,
    val parentUuid: String?,
)

/** Reads text from one conversation in a Claude data export. */
@Component
@Profile("shell")
class ClaudeExportParser(private val mapper: ObjectMapper = ObjectMapper()) {
    /**
     * Reads every leaf path of one conversation. The result holds one thread
     * per leaf record, in source order. A linear conversation gives one
     * thread.
     */
    fun parse(file: Path, selector: String): ClaudeConversation {
        require(selector.isNotBlank()) { "A conversation selector is required." }
        val root = Files.newBufferedReader(file).use(mapper::readTree)
        require(root.isArray) { "The export must contain a conversation array." }
        val matches = root.filter { item ->
            item.path("uuid").takeIf { it.isTextual }?.asText() == selector ||
                item.path("name").takeIf { it.isTextual }?.asText() == selector
        }
        require(matches.size == 1) { "Expected one conversation for '$selector', found ${matches.size}." }
        val conversation = matches.single()
        val uuid = conversation.field("uuid")
        requireUuid(uuid, "conversation")
        val turns = conversation.path("chat_messages")
        require(turns.isArray) { "Conversation $uuid has no chat_messages array." }

        val sourceTurns = turns.map { turn ->
            val messageId = turn.field("uuid")
            requireUuid(messageId, "message")
            val parentNode = turn.get("parent_message_uuid")
            val parentUuid = when {
                parentNode == null || parentNode.isNull -> null
                parentNode.isTextual && parentNode.asText().isNotBlank() -> parentNode.asText().also {
                    requireUuid(it, "parent message")
                }
                else -> throw IllegalArgumentException("Message $messageId has an invalid parent_message_uuid.")
            }
            ClaudeSourceTurn(turn, messageId, parentUuid)
        }
        val byId = sourceTurns.associateBy { it.uuid }
        require(byId.size == sourceTurns.size) { "Conversation $uuid has duplicate message UUIDs." }
        sourceTurns.forEach { source ->
            val visited = mutableSetOf<String>()
            var current: String? = source.uuid
            while (current != null && current in byId) {
                require(visited.add(current)) { "Conversation $uuid has a message parent cycle at $current." }
                current = byId.getValue(current).parentUuid
            }
        }

        // Read every record once. A record that two paths share is read here
        // one time, so each skip count stays a count of records.
        val extracted = mutableMapOf<String, ClaudeTurn?>()
        var skippedBlocks = 0
        var skippedAttachments = 0
        var skippedFiles = 0
        var skippedTurns = 0
        for (source in sourceTurns) {
            val turn = source.node
            val messageId = source.uuid
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
                val textParts = mutableListOf<String>()
                for (block in content) {
                    if (block.field("type") == "text") {
                        val part = block.path("text")
                        require(part.isTextual) { "Message $messageId has an invalid text block." }
                        val value = part.asText()
                        if (value.isBlank()) {
                            skippedBlocks++
                        } else {
                            textParts += value
                        }
                    } else {
                        skippedBlocks++
                    }
                }
                textParts.joinToString("\n\n")
            } else {
                turn.path("text").takeIf { it.isTextual }?.asText().orEmpty()
            }
            val attachments = turn.path("attachments").takeIf { it.isArray }?.toList().orEmpty()
            val attachmentNames = attachments.mapNotNull { it.path("file_name").takeIf(JsonNode::isTextual)?.asText() }.toSet()
            skippedAttachments += attachments.size
            skippedFiles += turn.path("files").takeIf { it.isArray }?.count { file ->
                val name = file.path("file_name").takeIf(JsonNode::isTextual)?.asText()
                name == null || name !in attachmentNames
            } ?: 0
            if (text.isBlank()) {
                skippedTurns++
                extracted[messageId] = null
            } else {
                extracted[messageId] = ClaudeTurn(messageId, sender, createdAt, text)
            }
        }

        val parentIds = sourceTurns.mapNotNull { it.parentUuid }.toSet()
        val threads = sourceTurns
            .filter { it.uuid !in parentIds }
            .map { leaf -> pathToLeaf(leaf.uuid, byId, extracted, uuid) }
        val name = conversation.path("name")
        require(name.isTextual) { "Conversation $uuid has no text name field." }
        return ClaudeConversation(
            uuid,
            name.asText(),
            threads,
            skippedBlocks,
            skippedAttachments,
            skippedTurns,
            skippedFiles,
        )
    }

    /** Walks the parent links of [leafUuid] and reads the path root to leaf. */
    private fun pathToLeaf(
        leafUuid: String,
        byId: Map<String, ClaudeSourceTurn>,
        extracted: Map<String, ClaudeTurn?>,
        conversationUuid: String,
    ): ClaudeThread {
        val path = mutableListOf<ClaudeTurn>()
        val visited = mutableSetOf<String>()
        var endsAtOmittedParent = false
        var current: String? = leafUuid
        while (current != null) {
            require(visited.add(current)) {
                "Conversation $conversationUuid has a message parent cycle at $current."
            }
            val source = byId[current]
            if (source == null) {
                endsAtOmittedParent = true
                break
            }
            extracted[current]?.let(path::add)
            current = source.parentUuid
        }
        return ClaudeThread(leafUuid, path.asReversed(), endsAtOmittedParent)
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