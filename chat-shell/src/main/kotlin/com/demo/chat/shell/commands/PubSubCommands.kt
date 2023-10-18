package com.demo.chat.shell.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatMessageService
import org.springframework.context.annotation.Profile
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption

@Profile("shell")
@ShellComponent
class PubSubCommands<T>(
    private val compositeServices: CompositeServiceBeans<T, String>,
    private val typeUtil: TypeUtil<T>,
    rootKeys: RootKeys<T>,
) : CommandsUtil<T>(typeUtil, rootKeys) {

    private val messageService: ChatMessageService<T, String> = compositeServices.messageService()

    @ShellMethod("Send a Message")
    fun send(
        @ShellOption topicId: String,
        @ShellOption messageText: String
    ) {
        val identity: T = identity("_")

        messageService
            .send(MessageSendRequest(messageText, identity, typeUtil.assignFrom(topicId)))
            .block()
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