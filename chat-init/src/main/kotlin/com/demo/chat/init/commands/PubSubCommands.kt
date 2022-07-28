package com.demo.chat.init.commands

import com.demo.chat.ByIdRequest
import com.demo.chat.MessageSendRequest
import com.demo.chat.init.domain.AdminKey
import com.demo.chat.init.domain.AnonymousKey
import com.demo.chat.service.edge.ChatMessageService
import org.springframework.context.annotation.Profile
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption

@Profile("shell")
@ShellComponent
class PubSubCommands<T>(
    private val messageService: ChatMessageService<T, String>,
    private val anonKey: AnonymousKey<T>,
    private val adminKey: AdminKey<T>,
    ) {

    @ShellMethod("Send a Message")
    fun send(
        @ShellOption topicId: T,
        @ShellOption messageText: String
    ) {
        messageService
            .send(MessageSendRequest(messageText, adminKey.id, topicId))
            .block()
    }


    @ShellMethod("Listen to a topic")
    fun listen(
        @ShellOption topicId: T
    ) = messageService.listenTopic(ByIdRequest(topicId))
        .doOnNext { message ->
            println("Message: ${message.key.from} : ${message.data}\n")
        }
        .subscribe()

}