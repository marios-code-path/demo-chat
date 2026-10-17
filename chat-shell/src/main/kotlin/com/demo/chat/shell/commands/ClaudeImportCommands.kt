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

/**
 * Names the topic of one thread: `<base>:<conversation uuid>:<generation>:<leaf uuid>`.
 *
 * The leaf uuid makes the name stable. An added leaf therefore never renames
 * an existing topic. A colon and a hyphen are safe in a name since PR #224
 * made the topic name a whole-value index match.
 */
fun claudeTopicName(
    base: String,
    conversationUuid: String,
    generation: String,
    leafUuid: String,
): String = "$base:$conversationUuid:$generation:$leafUuid"

/** Imports every thread of one Claude conversation through the RSocket clients. */
@Component
@Profile("shell")
class ClaudeImportCommands<T : Any>(
    private val services: CompositeServiceBeans<T, String>,
    private val login: LoginCommands<T>,
    private val parser: ClaudeExportParser,
    private val environment: Environment,
) {
    /**
     * Imports every leaf path of one conversation. Each path receives its own
     * topic. The operator names the generation, and the importer never
     * computes one.
     */
    fun importConversation(
        file: String,
        conversation: String,
        topic: String,
        generation: String,
        human: String,
        assistant: String,
    ): String {
        require(topic.isNotBlank()) { "Topic base '$topic' cannot be empty." }
        val generationValue = requireGeneration(generation)
        val export = Path.of(file)
        val parsed = parser.parse(export, conversation)
        // A logged-in Admin session needs no variable. Without one, the
        // variable logs in as Admin for this run. See CHAT-wmfemntw.
        val password = environment.getProperty("CHAT_IMPORT_ADMIN_PASSWORD")
        val adminSession = sessionIsAdmin()
        require(adminSession || !password.isNullOrBlank()) {
            "import-claude needs an Admin session. Log in as Admin, or set CHAT_IMPORT_ADMIN_PASSWORD."
        }
        val session = if (adminSession) "your session" else "CHAT_IMPORT_ADMIN_PASSWORD"
        // The map is read before any write. A map that cannot be read would
        // lose its entries on the first write of this run.
        val mapPath = ClaudeImportMap.pathFor(export, parsed.uuid, generationValue)
        val map = ClaudeImportMap.load(mapPath, parsed.uuid, generationValue, export)
        parsed.threads.forEach { map.checkTopicName(it.leafUuid, claudeTopicName(topic, parsed.uuid, generationValue, it.leafUuid)) }

        val previousUser = ShellStateConfiguration.loggedInUser
        val previousCredential = ShellStateConfiguration.loginMetadata
        try {
            if (!adminSession) login.login(ADMIN, password!!)
            val users = services.userService()
            val humanId = exactUserId(users.findByUsername(ByStringRequest(human)).collectList().block().orEmpty(), human)
            val assistantId = exactUserId(users.findByUsername(ByStringRequest(assistant)).collectList().block().orEmpty(), assistant)

            val topics = services.topicService()
            val messages = services.messageService()
            var topicsCreated = 0
            var messagesImported = 0
            val omittedParents = mutableListOf<String>()
            parsed.threads.forEach { thread ->
                val name = claudeTopicName(topic, parsed.uuid, generationValue, thread.leafUuid)
                val existing = topics.getRoomByName(ByStringRequest(name))
                    .onErrorResume(CoreNotFound::class.java) { Mono.empty() }
                    .block()
                require(existing == null || existing.data == name) { "Room lookup did not return the exact name '$name'." }
                val roomId = existing?.key?.id ?: run {
                    topicsCreated++
                    requireNotNull(topics.addRoom(ByStringRequest(name)).block()) {
                        "Room '$name' was not created."
                    }.id
                }
                map.putTopic(thread.leafUuid, name, roomId.toString())
                if (thread.pathEndsAtOmittedParent) {
                    omittedParents += thread.leafUuid
                }
                thread.messages.forEach { turn ->
                    // A shared parent lands in each topic of a fork. The route
                    // refuses one request ID across two rooms. The topic ID
                    // scopes the request ID, so each topic admits the record.
                    val sourceId = "$roomId:${turn.uuid}"
                    require("import:$sourceId".length <= 128) { "Message ${turn.uuid} has a request ID above 128 characters." }
                    val senderId = if (turn.sender == "human") humanId else assistantId
                    val imported = requireNotNull(messages.importMessage(MessageImportRequest(turn.text, senderId, roomId, turn.createdAt, sourceId)).block()) {
                        "Message ${turn.uuid} returned no import result."
                    }
                    map.messages[sourceId] = ClaudeMapMessage(imported.key.id.toString(), roomId.toString(), name)
                    messagesImported++
                }
                // Each thread writes the map, so a later failure keeps the
                // entries of the threads before it.
                map.write(mapPath)
            }
            return "Imported as Admin ($session). " +
                "Topics created: $topicsCreated of ${parsed.threads.size}. " +
                "Messages imported: $messagesImported. " +
                "Missing parents: ${omittedParents.size}" +
                (if (omittedParents.isEmpty()) ". " else " for ${omittedParents.joinToString(", ")}. ") +
                "Skipped turns: ${parsed.skippedTurns}. Skipped content blocks: ${parsed.skippedBlocks}. " +
                "Skipped attachments: ${parsed.skippedAttachments}. Skipped files: ${parsed.skippedFiles}. " +
                "Map: $mapPath."
        } finally {
            ShellStateConfiguration.loggedInUser = previousUser
            ShellStateConfiguration.loginMetadata = previousCredential
        }
    }

    /** Reads a decimal generation. The value enters a topic name alone. */
    private fun requireGeneration(generation: String): String {
        val value = generation.trim()
        require(value.isNotEmpty() && value.all { it.isDigit() }) {
            "Generation '$generation' must be a decimal value."
        }
        val number = value.toIntOrNull()
        require(number != null) { "Generation '$generation' is above the accepted range." }
        return number.toString()
    }

    /**
     * True when the shell is logged in as the user with the exact handle
     * [ADMIN]. The check compares user IDs, so `login admin` counts. A shell
     * with no login is not Admin, and the check then makes no request.
     */
    private fun sessionIsAdmin(): Boolean {
        val current = ShellStateConfiguration.loggedInUser.orElse(null) ?: return false
        if (ShellStateConfiguration.loginMetadata.isEmpty) return false
        val admins = services.userService().findByUsername(ByStringRequest(ADMIN)).collectList().block().orEmpty()
        return admins.any { it.handle == ADMIN && it.key.id == current }
    }

    private fun exactUserId(found: List<com.demo.chat.domain.User<T>>, handle: String): T {
        val exact = found.filter { it.handle == handle }
        require(exact.size == 1) { "Expected one user with exact handle '$handle', found ${exact.size}." }
        return exact.single().key.id
    }

    private companion object {
        /** The handle of the Admin user that `userinit.yml` creates. */
        const val ADMIN = "Admin"
    }
}