package com.demo.chat.mcp.tool

import com.demo.chat.mcp.client.ClientException
import com.demo.chat.mcp.client.FailureReason
import com.demo.chat.mcp.client.Topic
import com.demo.chat.mcp.client.TopicClient
import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.config.AdapterId
import com.demo.chat.mcp.config.ConfigException
import com.demo.chat.mcp.config.parseIdText

/**
 * The two topic rules.
 *
 * This type holds no MCP type. A test drives it with no server and no
 * transport, and the MCP layer supplies nothing but the arguments.
 */
class TopicToolService(
    private val config: AdapterConfig,
    private val topics: TopicClient,
) {
    /**
     * Read every configured topic.
     *
     * An unavailable or denied topic is left out. The list carries no name and
     * no count for it. A refused credential, a transport failure and a backend
     * failure each fail the whole list.
     */
    fun listTopics(): List<TopicView> = config.topicIds.mapNotNull { id -> readOrOmit(id) }

    /**
     * Read one topic that the configuration allows.
     *
     * An argument outside the configured topic list is refused here, before
     * any backend call.
     */
    fun getTopic(topicId: String): TopicView = TopicView.of(read(allowedId(topicId)))

    /** Read one topic, or omit it when the backend does not serve it. */
    private fun readOrOmit(id: AdapterId): TopicView? =
        try {
            TopicView.of(read(id))
        } catch (failure: ClientException) {
            if (failure.reason == FailureReason.NOT_AVAILABLE) {
                null
            } else {
                throw failure
            }
        }

    private fun read(id: AdapterId): Topic = topics.readTopic(id)

    /**
     * Refuse a topic argument outside the configured topic list.
     *
     * The order matters. The id rules run first, so a malformed value fails as
     * an id. The allowlist runs second, so a well formed id that the operator
     * did not name fails as a topic.
     */
    private fun allowedId(text: String): AdapterId {
        val id =
            try {
                parseIdText(text, config.keyType)
            } catch (failure: ConfigException) {
                throw ToolException("the topic id is refused: ${failure.message}")
            }
        if (config.topicIds.none { it == id }) {
            throw ToolException("the topic id is not one of the configured topic ids")
        }
        return id
    }
}
