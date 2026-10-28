package com.demo.chat.shell.portal

import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.TopicCommands
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * The register gateway over the shell commands.
 *
 * Every call runs as the logged-in user. The user id `_` asks the shell for
 * that user, so the portal keeps one fixed login identity. See CHAT-secgyzgg.
 */
class ShellRegisterGateway(
    private val commands: PubSubCommands<*>,
    private val topics: TopicCommands<*>,
    private val mapper: ObjectMapper,
    private val maxAge: Duration,
) : RegisterGateway {

    override fun readIndex(register: String, deadline: Duration): List<JsonNode> =
        parse(commands.registerRead(register, maxAge, deadline))

    override fun join(topic: String) {
        topics.join(LOGGED_IN_USER, topic)
    }

    override fun addRoom(name: String) {
        topics.addTopic(LOGGED_IN_USER, name)
            ?: throw IllegalStateException("the core answered no room for $name")
    }

    override fun post(topic: String, text: String, requestId: String): JsonNode =
        mapper.readTree(commands.post(topic, text, requestId))

    override fun readMessages(topic: String): List<JsonNode> =
        parse(commands.messages(topic, format = "records"))

    /** Split one line per record. A blank answer is an empty list. */
    private fun parse(text: String): List<JsonNode> =
        text.lineSequence().filter { it.isNotBlank() }.map { mapper.readTree(it) }.toList()

    private companion object {
        /** The shell id of the logged-in user. */
        const val LOGGED_IN_USER = "_"
    }
}
