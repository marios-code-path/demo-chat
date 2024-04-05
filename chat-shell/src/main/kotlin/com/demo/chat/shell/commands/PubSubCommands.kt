package com.demo.chat.shell.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService
import org.springframework.context.annotation.Profile
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import reactor.core.publisher.Mono

@Profile("shell")
@ShellComponent
class PubSubCommands<T>(
    private val compositeServices: CompositeServiceBeans<T, String>,
    private val typeUtil: TypeUtil<T>,
    rootKeys: RootKeys<T>,
) : CommandsUtil<T>(typeUtil, rootKeys) {

    private val messageService: ChatMessageService<T, String> = compositeServices.messageService()
    private val topicService: ChatTopicService<T, String> = compositeServices.topicService()
    private val userService: ChatUserService<T> = compositeServices.userService()

    @ShellMethod("Send a Message")
    fun send(
        @ShellOption(defaultValue = "_") topicName: String,
        @ShellOption(defaultValue = "_") topicId: String,
        @ShellOption(defaultValue = "_") userName: String,
        @ShellOption messageText: String
    ) {
        val identity: T = identity("_")

        if (!topicId.equals("_")) {
            messageService
                .send(MessageSendRequest(messageText, identity, typeUtil.assignFrom(topicId)))
                .doOnNext { key ->
                    println("Message Id: ${key.id}")
                }
                .block()

            return
        }

        if (!topicName.equals("_")) {
            topicService
                .getRoomByName(ByStringRequest(topicName))
                .flatMap {
                    messageService
                        .send(MessageSendRequest(messageText, identity, typeUtil.assignFrom(topicId)))
                }
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
    }

    @ShellMethod("Listen to a topic")
    fun listen(
        @ShellOption topicId: String
    ) = messageService.listenTopic(ByIdRequest(typeUtil.assignFrom(topicId)))
        .doOnNext { message ->
            println("Message: ${message.key.from} : ${message.data}\n")
        }
        .subscribe()
}