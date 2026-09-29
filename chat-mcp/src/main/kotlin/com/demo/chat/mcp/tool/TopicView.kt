package com.demo.chat.mcp.tool

import com.demo.chat.mcp.client.Topic
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One topic as a tool answers it.
 *
 * Both ids are JSON strings. A Long id above 2^53 keeps every digit, because
 * the text never passes through a number.
 *
 * The backend names the topic text `data`. The tool names it `name`, because
 * that is what the field holds.
 */
data class TopicView(
    val id: String,
    val root: String,
    val name: String,
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("id", id)
            put("root", root)
            put("name", name)
        }

    companion object {
        fun of(topic: Topic): TopicView = TopicView(id = topic.id, root = topic.root, name = topic.data)
    }
}
