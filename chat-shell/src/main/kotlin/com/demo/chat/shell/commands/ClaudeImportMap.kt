package com.demo.chat.shell.commands

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** The topic of one leaf. */
data class ClaudeMapTopic(val topic: String, val roomId: String)

/** The message that one room-scoped source id produced. */
data class ClaudeMapMessage(val messageId: String, val roomId: String, val topic: String)

/**
 * The source-to-chat map of one conversation and generation. See `CHAT-zxrgxdlx`.
 *
 * An imported message id is a name-based UUID, and that derivation is one way.
 * The map keeps the reverse direction. [messages] is keyed by the room-scoped
 * source id `<roomId>:<messageUuid>`, and [topics] by the leaf uuid.
 *
 * A change to the derivation input orphans every imported message. Keep the
 * map before such a change.
 */
class ClaudeImportMap(
    val conversation: String,
    val generation: String,
    var export: String,
    val topics: MutableMap<String, ClaudeMapTopic> = linkedMapOf(),
    val messages: MutableMap<String, ClaudeMapMessage> = linkedMapOf(),
) {
    fun messageId(sourceId: String): String? = messages[sourceId]?.messageId

    /**
     * Refuses a leaf that the map holds under another topic name. A second
     * topic base names a second room for one leaf, and a write would replace
     * the first room while its message entries stay.
     */
    fun checkTopicName(leaf: String, topic: String) {
        val earlier = topics[leaf] ?: return
        check(earlier.topic == topic) {
            "The import map holds leaf $leaf as topic ${earlier.topic}, not $topic. " +
                "Use the earlier topic base, or another generation."
        }
    }

    /**
     * Records the room of a leaf. A leaf that the map holds in another room
     * is refused. A recreated store gives a topic name a new room.
     */
    fun putTopic(leaf: String, topic: String, roomId: String) {
        checkTopicName(leaf, topic)
        val earlier = topics[leaf]
        check(earlier == null || earlier.roomId == roomId) {
            "The import map holds leaf $leaf of topic $topic in room ${earlier?.roomId}, not room $roomId. " +
                "Move the map aside before an import into the new room."
        }
        topics[leaf] = ClaudeMapTopic(topic, roomId)
    }

    /**
     * Writes a temporary file beside the map, and then moves it over the map.
     * A failed write therefore never leaves a partial map.
     */
    fun write(path: Path) {
        val root = mapper.createObjectNode()
        root.put("conversation", conversation)
        root.put("generation", generation)
        root.put("export", export)
        val topicNode = root.putObject("topics")
        topics.forEach { (leaf, entry) ->
            topicNode.putObject(leaf).put("topic", entry.topic).put("roomId", entry.roomId)
        }
        val messageNode = root.putObject("messages")
        messages.forEach { (sourceId, entry) ->
            messageNode.putObject(sourceId)
                .put("messageId", entry.messageId).put("roomId", entry.roomId).put("topic", entry.topic)
        }
        try {
            val partial = Files.createTempFile(path.parent, "${path.fileName}.", ".tmp")
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(partial.toFile(), root)
                Files.move(partial, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(partial)
            }
        } catch (e: IOException) {
            throw IllegalStateException("The import map $path was not written. The run stops, because a lost map loses the reverse lookup.", e)
        }
    }

    companion object {
        private val mapper = ObjectMapper()

        /** The map file sits beside the export file. */
        fun pathFor(export: Path, conversation: String, generation: String): Path =
            export.toAbsolutePath().parent.resolve("claude-import-map-$conversation-$generation.json")

        fun read(path: Path): ClaudeImportMap {
            val root = try {
                mapper.readTree(path.toFile())
            } catch (e: IOException) {
                throw IllegalStateException("The import map $path cannot be read.", e)
            }
            fun text(node: JsonNode, field: String): String {
                val value = node.get(field)
                check(value != null && value.isTextual) { "The import map $path has no text field '$field'." }
                return value.asText()
            }
            // An absent or scalar section would read as empty, and the next
            // write would erase it.
            fun section(field: String): JsonNode {
                val value = root.get(field)
                check(value != null && value.isObject) { "The import map $path has no object field '$field'." }
                return value
            }
            val map = ClaudeImportMap(text(root, "conversation"), text(root, "generation"), text(root, "export"))
            section("topics").properties().forEach { (leaf, entry) ->
                map.topics[leaf] = ClaudeMapTopic(text(entry, "topic"), text(entry, "roomId"))
            }
            section("messages").properties().forEach { (sourceId, entry) ->
                map.messages[sourceId] = ClaudeMapMessage(text(entry, "messageId"), text(entry, "roomId"), text(entry, "topic"))
            }
            return map
        }

        /**
         * Reads the map of [conversation] and [generation], or starts an empty one.
         * A map of another conversation or generation stops the run.
         */
        fun load(path: Path, conversation: String, generation: String, export: Path): ClaudeImportMap {
            val exportPath = export.toAbsolutePath().toString()
            if (!Files.exists(path)) return ClaudeImportMap(conversation, generation, exportPath)
            val map = read(path)
            check(map.conversation == conversation && map.generation == generation) {
                "The import map $path holds conversation ${map.conversation} generation ${map.generation}, " +
                    "not conversation $conversation generation $generation."
            }
            map.export = exportPath
            return map
        }
    }
}
