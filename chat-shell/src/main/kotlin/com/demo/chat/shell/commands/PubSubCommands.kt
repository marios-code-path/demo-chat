package com.demo.chat.shell.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.security.rsocket.CoreAuthorizationRefusal
import com.demo.chat.security.rsocket.CoreNotFound
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.time.format.DateTimeFormatter
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.Exceptions
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import com.demo.chat.shell.cli.ShellExit
import com.demo.chat.shell.cli.ShellExitStatus

@Profile("shell")
@Component
class PubSubCommands<T : Any>(
    private val compositeServices: CompositeServiceBeans<T, String>,
    private val typeUtil: TypeUtil<T>,
    private val roots: RootKeys<T>,
    private val rooms: ShellRooms<T>,
) : CommandsUtil<T>(typeUtil, roots) {

    private val messageService: ChatMessageService<T, String> = compositeServices.messageService()
    private val userService: ChatUserService<T> = compositeServices.userService()

    /**
     * [topic] is a room name or a room id. See `CHAT-scoizkpm`. [requestId] `_`
     * creates a new ID. Repeat a send with the printed request ID to recover
     * its receipt. The server binds the sender to the login, so the shell
     * sends none.
     *
     * The answer is the request ID, the message ID, the command ID, and the
     * outcome, one on each line.
     */
    fun send(topic: String, userName: String, messageText: String, requestId: String): String {
        val id = if (requestId == "_") UUID.randomUUID().toString() else requestId
        val dest: Mono<T> = when {
            topic != "_" -> Mono.just(rooms.idOf(topic))
            userName != "_" -> userService.findByUsername(ByStringRequest(userName)).collectList().flatMap { users ->
                if (users.size != 1) Mono.error(ChatException("Problem Finding a Definite User: $userName"))
                else Mono.just(users[0].key.id)
            }
            else -> throw IllegalArgumentException("send needs --topic or --userName.")
        }
        return dest.flatMap { messageService.submit(MessageSubmitRequest(messageText, it, id)) }
            .map { result ->
                listOf(
                    "Request Id: $id",
                    "Message Id: ${result.receipt.messageKey.id}",
                    "Command Id: ${result.receipt.commandId}",
                    "Outcome: ${result.outcome}",
                ).joinToString("\n")
            }
            .block().orEmpty()
    }

    /**
     * Sends [text] to the room that [topic] names, and answers one JSON line.
     *
     * The line holds `messageId`, `commandId`, `requestId`, and `outcome`.
     * The text arrives unchanged, because it came from the command input and
     * not from an argument. [requestId] `_` creates a new ID, as `send` does.
     */
    fun post(topic: String, text: String, requestId: String): String {
        val id = if (requestId == "_") UUID.randomUUID().toString() else requestId
        val result = messageService.submit(MessageSubmitRequest(text, rooms.idOf(topic), id)).block()
            ?: throw IllegalStateException("the core answered no receipt for request $id")
        return mapper.writeValueAsString(linkedMapOf(
            "messageId" to typeUtil.toString(result.receipt.messageKey.id),
            "commandId" to result.receipt.commandId,
            "requestId" to id,
            "outcome" to result.outcome.toString(),
        ))
    }

    /** Answers the state of each backend of a command that the login owns. */
    fun commandStatus(commandId: String): String =
        messageService.commandStatus(CommandStatusRequest(commandId))
            .map { status ->
                (listOf("Command Id: ${status.commandId}") +
                    status.backends.map { (backend, state) -> "$backend: ${state.state}" }).joinToString("\n")
            }
            .block().orEmpty()

    /**
     * Listens to the room that [topic] names, and answers its id.
     *
     * The listener is stored under the room id, so a `hangup` by name and a
     * `hangup` by id find the same listener.
     */
    fun listen(
        topic: String
    ): String {
        val id = rooms.idOf(topic)
        val topicId = typeUtil.toString(id)
        // A refusal ends the stream with an error. Without this handler the
        // error was dropped, and a refused listen printed nothing. CHAT-lfaajjcj.
        val d = messageService.listenTopic(ByIdRequest(id))
            .doOnNext { message ->
                println("Message: ${message.key.from} : ${body(message)}\n")
            }
            .doOnError { error ->
                ShellStateConfiguration.listeners.remove(topicId)
                println("Listen on topic $topicId ended: ${error.message}\n")
            }
            .onErrorComplete()
            .subscribe()

        ShellStateConfiguration.listeners[topicId] = d
        return topicId
    }

    /**
     * Stops the listener of the room that [topic] names, and answers its id.
     *
     * A stored room id stops with no lookup, so a listener on a removed room
     * still stops. Any other value resolves as a room name or a room id.
     */
    fun hangup(topic: String): String {
        val topicId = if (ShellStateConfiguration.listeners.containsKey(topic)) topic
        else typeUtil.toString(rooms.idOf(topic))
        ShellStateConfiguration.listeners.remove(topicId)?.dispose()
        return topicId
    }

    /**
     * This command displays stored messages in time order. See `CHAT-bmmtojqm`.
     * The service checks SUBSCRIBE and preserves its Access Denied refusal.
     *
     * [since] keeps the messages at or after that instant. [limit] then keeps
     * the newest ones. **The `records` [format] prints one JSON line per
     * message** with `id`, `topic`, `sender`, `timestamp`, and `text`. The
     * timestamp is the stored instant in UTC at its full precision, and the
     * text is the body unchanged. An empty result prints nothing in that
     * format.
     */
    fun messages(topic: String, limit: Int? = null, format: String = "human", since: Instant? = null): String {
        require(limit == null || limit > 0) { "messages needs a positive --limit." }
        require(format == "human" || format == "records") { "messages needs --format human or records." }
        val room = rooms.room(topic)
        val stored = storedMessages(room)
            .filter { since == null || !it.key.timestamp.isBefore(since) }
        if (stored.isEmpty()) return if (format == "records") "" else "No messages in ${room.data}."

        val selected = if (limit == null) stored else stored.takeLast(limit)
        val handles = selected.map { it.key.from }.distinct().associateWith(::handleOf)
        if (format == "records") return selected.joinToString("\n") { record(room, it, handles.getValue(it.key.from)) }
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            .withZone(ZoneId.systemDefault())
        return selected.joinToString("\n") { message ->
            val prefix = "${formatter.format(message.key.timestamp)} | ${handles.getValue(message.key.from)} | "
            prefix + body(message).lines().joinToString("\n" + " ".repeat(prefix.length))
        }
    }

    /** The handle of a sender, or its key id when the user is missing. */
    private fun handleOf(sender: T): String =
        userService.findByUserId(ByIdRequest(sender))
            .map { it.handle }
            .onErrorResume(CoreNotFound::class.java) { Mono.empty() }
            .block() ?: typeUtil.toString(sender)

    /** One message as one JSON line: `id`, `topic`, `sender`, `timestamp`, and `text`. */
    private fun record(room: MessageTopic<T>, message: Message<T, String>, handle: String): String =
        mapper.writeValueAsString(linkedMapOf(
            "id" to typeUtil.toString(message.key.id),
            "topic" to room.data,
            "sender" to handle,
            "timestamp" to message.key.timestamp.toString(),
            "text" to body(message),
        ))

    /** The stored messages of a room, oldest first. Equal times order by message id. */
    private fun storedMessages(room: MessageTopic<T>): List<Message<T, String>> =
        messageService.listMessages(ByIdRequest(room.key.id)).collectList().block().orEmpty()
            .sortedWith(compareBy<Message<T, String>> { it.key.timestamp }.thenBy { typeUtil.toString(it.key.id) })

    /**
     * The session-start read of the topic register, as record lines.
     *
     * It prints every pointer of [index], oldest first. It then prints the
     * newest entry of each topic that a pointer names, if that entry is younger
     * than [maxAge]. A pointer reads `<key> | <topic> | <metadata> | <title>`.
     * A key is opaque text that names one topic. The read does not know where
     * a key comes from. It never reads the metadata field or the title.
     * A topic that two pointers name is read once. A pointer that does not
     * follow the form fails the read, and so does any read that fails with an
     * error other than a denial. **A denied topic prints a warning line and
     * the read continues**, because an agent is not a member of every topic
     * that the index names. A denied index prints one warning line and no
     * records.
     *
     * **[limit] is the hard limit of owner decision 20.** At the limit the
     * read ends with a [ShellExit] of status [ShellExitStatus.TIMEOUT]. The
     * worker thread can go on after that, so the caller must end the process.
     * An empty index answers an empty text.
     *
     * Two pointers can name one key. The earliest pointer is the valid one,
     * by stored time and then by message id. The read prints every pointer,
     * reads the topic of the earliest pointer alone, and prints one warning
     * line for each later pointer. An archive pointer uses `-` as its key,
     * and many archive pointers are normal. See the design, "Concurrent
     * writes".
     */
    fun registerRead(index: String, maxAge: Duration, limit: Duration, now: Instant = Instant.now()): String =
        Mono.fromCallable { readRegister(index, maxAge, now) }
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(limit, Mono.error(ShellExit(ShellExitStatus.TIMEOUT, "register-read passed its ${limit.toMillis()} ms limit")))
            .block().orEmpty()

    /** One index pointer, split into its key and its topic name. */
    private class Pointer<T>(val message: Message<T, String>, val key: String, val topic: String)

    private fun readRegister(index: String, maxAge: Duration, now: Instant): String {
        val indexRoom = try {
            rooms.room(index)
        } catch (error: Exception) {
            if (denial(error) == null) throw error
            return deniedLine("denied-index", index)
        }
        val pointers = try {
            storedMessages(indexRoom).map { pointer ->
                // The split rule is shared with node-9/bin/register. Split on
                // |, trim each field, and require exactly four fields, a key
                // with no backslash, and a non-blank key and topic. The bash
                // tool reads through a tab-separated form that doubles a
                // backslash, so it cannot match one. The decoder fixtures hold
                // the cases. See the design, decision 39.
                val fields = pointer.data.split("|").map { it.trim() }
                check(fields.size == 4 && fields[0].isNotBlank() && fields[1].isNotBlank() &&
                    !fields[0].contains('\\')) {
                    "The pointer '${pointer.data.lineSequence().first()}' in $index is not '<key> | <topic> | <metadata> | <title>'."
                }
                Pointer(pointer, fields[0], fields[1])
            }
        } catch (error: Exception) {
            if (denial(error) == null) throw error
            return deniedLine("denied-index", index)
        }

        val kept = LinkedHashMap<String, Pointer<T>>()
        val warnings = mutableListOf<String>()
        for (pointer in pointers) {
            if (pointer.key == "-") continue
            val earliest = kept.putIfAbsent(pointer.key, pointer)
            if (earliest != null) warnings += warningLine(pointer, earliest)
        }
        val topicNames = pointers.filter { it.key == "-" || kept[it.key] === it }.map { it.topic }.distinct()

        val cutoff = now.minus(maxAge)
        val entries = topicNames.mapNotNull { name ->
            try {
                val room = rooms.room(name)
                storedMessages(room).lastOrNull()?.takeIf { it.key.timestamp.isAfter(cutoff) }?.let { room to it }
            } catch (error: Exception) {
                if (denial(error) == null) throw error
                warnings += deniedLine("denied-topic", name)
                null
            }
        }

        val handles = HashMap<T, String>()
        fun handle(sender: T) = handles.getOrPut(sender) { handleOf(sender) }
        return (pointers.map { registerRecord(indexRoom, it.message, handle(it.message.key.from)) } +
            entries.map { (room, entry) -> registerRecord(room, entry, handle(entry.key.from)) } +
            warnings).joinToString("\n")
    }

    /**
     * One register read line. It carries `type`, so a warning cannot hide
     * among records. The `messages` record output keeps its five fields.
     */
    private fun registerRecord(room: MessageTopic<T>, message: Message<T, String>, handle: String): String =
        mapper.writeValueAsString(linkedMapOf(
            "type" to "record",
            "id" to typeUtil.toString(message.key.id),
            "topic" to room.data,
            "sender" to handle,
            "timestamp" to message.key.timestamp.toString(),
            "text" to body(message),
        ))

    /**
     * The denial inside [error], or null when [error] is another failure.
     * The server answers [AccessDeniedException], and a client decodes the
     * same refusal as [CoreAuthorizationRefusal].
     */
    private fun denial(error: Throwable): Throwable? =
        Exceptions.unwrap(error).takeIf { it is AccessDeniedException || it is CoreAuthorizationRefusal }

    /** One warning line for a denied read. The line names the topic and the reason. */
    private fun deniedLine(kind: String, topic: String): String =
        mapper.writeValueAsString(linkedMapOf(
            "type" to "warning",
            "kind" to kind,
            "topic" to topic,
            "reason" to "Access Denied",
        ))

    /** One warning for a later pointer of a key that the index already holds. */
    private fun warningLine(ignored: Pointer<T>, kept: Pointer<T>): String =
        mapper.writeValueAsString(linkedMapOf(
            "type" to "warning",
            "kind" to "duplicate-pointer",
            "key" to ignored.key,
            "kept" to linkedMapOf(
                "topic" to kept.topic,
                "id" to typeUtil.toString(kept.message.key.id)),
            "ignored" to linkedMapOf(
                "topic" to ignored.topic,
                "id" to typeUtil.toString(ignored.message.key.id)),
        ))

    private companion object {
        val mapper = ObjectMapper()
    }

    /** A pointer shows its target and no text. No read of the target runs. See `CHAT-erkoxybm`. */
    // The static type Message<T, String> fixes T, so `is PointerMessage<T>` compiles.
    // Measured with kotlinc, K1 and K2, on 2026-10-10. A subject of type Any fails.
    private fun body(message: Message<T, String>): String =
        if (message is PointerMessage<T>) {
            val domain = when (roots.domainOfRoot(message.target.root)) {
                ChatDomain.MESSAGE_TOPIC -> "topic"
                else -> "message"
            }
            "→ $domain ${typeUtil.toString(message.target.id)}"
        } else message.data
}
