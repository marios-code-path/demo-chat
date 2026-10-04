package com.demo.chat.shell.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.shell.deploy.ShellStateConfiguration
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatUserService
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

    /** [topic] is a room name or a room id. See `CHAT-scoizkpm`. */
    fun send(
        topic: String,
        userName: String,
        messageText: String
    ) {
        val identity: T = identity("_")

        if (!topic.equals("_")) {
            messageService
                .send(MessageSendRequest(messageText, identity, rooms.idOf(topic)))
                .doOnNext { key ->
                    println("Message Id: ${key.id}")
                }
                .block()

            return
        }

        // TODO this makes me think: re-do the whole app-sided username constraint.   Figure out how to create the
        // TODO constraint close to the service itself.
        if (!userName.equals("_")) {
            userService
                .findByUsername(ByStringRequest(userName))
                .collectList()
                .flatMap { users ->
                    if (users.size > 1)
                        return@flatMap Mono.error(ChatException("Problem Finding a Definite User: ${userName}"))

                    messageService
                        .send(MessageSendRequest(messageText, identity, users.get(0).key.id))
                }
                .doOnNext { key ->
                    println("Message Id: ${key.id}")
                }
                .block()

            return
        }

        throw IllegalArgumentException("send needs --topic or --userName.")
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
     * The stored messages of the room that [topic] names, one line each.
     *
     * The read checks `SUBSCRIBE` on the room, as `listen` does. A refusal
     * reaches the caller as `Access Denied`. See `CHAT-rghaeqsa`.
     */
    fun messages(topic: String): String {
        val room = rooms.room(topic)
        val lines = messageService
            .listMessages(ByIdRequest(room.key.id))
            .map { message -> "${message.key.id} | ${message.key.from} | ${message.data}" }
            .collectList()
            .block()
            .orEmpty()
        return if (lines.isEmpty()) "No messages in ${room.data}."
        else lines.joinToString("\n")
    }
}
