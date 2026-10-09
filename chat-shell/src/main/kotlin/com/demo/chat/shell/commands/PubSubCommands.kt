package com.demo.chat.shell.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.security.rsocket.CoreNotFound
import java.time.ZoneId
import java.util.UUID
import java.time.format.DateTimeFormatter
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Profile("shell")
@Component
class PubSubCommands<T : Any>(
    private val compositeServices: CompositeServiceBeans<T, String>,
    private val typeUtil: TypeUtil<T>,
    rootKeys: RootKeys<T>,
    private val rooms: ShellRooms<T>,
) : CommandsUtil<T>(typeUtil, rootKeys) {

    private val messageService: ChatMessageService<T, String> = compositeServices.messageService()
    private val userService: ChatUserService<T> = compositeServices.userService()

    /**
     * [topic] is a room name or a room id. See `CHAT-scoizkpm`. [requestId] `_`
     * creates a new ID. Repeat a send with the printed request ID to recover
     * its receipt. The server binds the sender to the login, so the shell
     * sends none.
     */
    fun send(topic: String, userName: String, messageText: String, requestId: String) {
        val id = if (requestId == "_") UUID.randomUUID().toString() else requestId
        val dest: Mono<T> = when {
            topic != "_" -> Mono.just(rooms.idOf(topic))
            userName != "_" -> userService.findByUsername(ByStringRequest(userName)).collectList().flatMap { users ->
                if (users.size != 1) Mono.error(ChatException("Problem Finding a Definite User: $userName"))
                else Mono.just(users[0].key.id)
            }
            else -> throw IllegalArgumentException("send needs --topic or --userName.")
        }
        dest.flatMap { messageService.submit(MessageSubmitRequest(messageText, it, id)) }
            .doOnNext { result ->
                println("Request Id: $id")
                println("Message Id: ${result.receipt.messageKey.id}")
                println("Command Id: ${result.receipt.commandId}")
                println("Outcome: ${result.outcome}")
            }
            .block()
    }

    /** Prints the state of each backend of a command that the login owns. */
    fun commandStatus(commandId: String) {
        messageService.commandStatus(CommandStatusRequest(commandId))
            .doOnNext { status ->
                println("Command Id: ${status.commandId}")
                status.backends.forEach { (backend, state) -> println("$backend: ${state.state}") }
            }
            .block()
    }

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
                println("Message: ${message.key.from} : ${message.data}\n")
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
     */
    fun messages(topic: String, limit: Int? = null): String {
        require(limit == null || limit > 0) { "messages needs a positive --limit." }
        val room = rooms.room(topic)
        val stored = messageService.listMessages(ByIdRequest(room.key.id))
            .collectList().block().orEmpty()
            .sortedBy { it.key.timestamp }
        if (stored.isEmpty()) return "No messages in ${room.data}."

        val selected = if (limit == null) stored else stored.takeLast(limit)
        val handles = selected.map { it.key.from }.distinct().associateWith { sender ->
            userService.findByUserId(ByIdRequest(sender))
                .map { it.handle }
                .onErrorResume(CoreNotFound::class.java) { Mono.empty() }
                .block() ?: typeUtil.toString(sender)
        }
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            .withZone(ZoneId.systemDefault())
        return selected.joinToString("\n") { message ->
            val prefix = "${formatter.format(message.key.timestamp)} | ${handles.getValue(message.key.from)} | "
            prefix + message.data.lines().joinToString("\n" + " ".repeat(prefix.length))
        }
    }
}
