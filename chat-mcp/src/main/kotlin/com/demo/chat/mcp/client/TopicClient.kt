package com.demo.chat.mcp.client

import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.config.AdapterId
import com.demo.chat.mcp.config.readCredentialFile
import java.net.URI

/**
 * The topic reader of one adapter.
 *
 * One adapter reaches one deployment with one credential. The credential is
 * read from its file at each request. This type never holds one.
 *
 * The transport is required and it has no default. **The adapter owns one
 * transport for the whole process.** A default would build one transport for
 * each client, and the concurrency limit would then belong to one client
 * rather than to the process. See the ownership rule in `McpAdapterMain`.
 */
class TopicClient(
    private val config: AdapterConfig,
    private val http: BackendHttp,
) {
    /**
     * Read one topic.
     *
     * The caller supplies an id from the configured topic list. A model never
     * supplies one.
     */
    fun readTopic(id: AdapterId): Topic {
        val credential = usableCredential(readCredentialFile(config.credentialFile))
        return decodeTopic(http.get(topicUri(config.backendBaseUrl, id), credential), config.keyType)
    }
}

/**
 * Build the topic route.
 *
 * The id enters the path as exact text. A Long id above 2^53 keeps every
 * digit, because the text comes from the configuration and not from a number.
 */
internal fun topicUri(origin: URI, id: AdapterId): URI = origin.resolve("/topic/id/${id.text}")

/**
 * Refuse a credential that would break an HTTP header.
 *
 * `java.net.http.HttpRequest` refuses such a value too. The check runs here so
 * the failure names the file and not a request builder.
 */
internal fun usableCredential(text: String): String {
    if (text.any { it < ' ' || it == '\u007f' }) {
        throw ClientException(
            "the credential file holds a control character",
            FailureReason.AUTHENTICATION,
        )
    }
    return text
}
