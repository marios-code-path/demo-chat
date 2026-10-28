package com.demo.chat.shell.portal

import com.fasterxml.jackson.databind.JsonNode
import java.time.Duration

/**
 * The register calls that a write needs.
 *
 * The interface stays free of Spring annotations, so a test can replace it.
 * Each call maps to one shell command. See CHAT-secgyzgg.
 */
interface RegisterGateway {
    /** Read the pointer index. A denied index answers a `denied-index` warning line. */
    fun readIndex(deadline: Duration): List<JsonNode>

    /** Join one room by name. */
    fun join(topic: String)

    /** Create one room by name. */
    fun addRoom(name: String)

    /** Post [text] to [topic]. Answer the receipt. */
    fun post(topic: String, text: String, requestId: String): JsonNode

    /** Read the records of [topic], oldest first. */
    fun readMessages(topic: String): List<JsonNode>
}
