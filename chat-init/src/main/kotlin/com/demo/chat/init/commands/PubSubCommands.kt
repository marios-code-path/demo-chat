package com.demo.chat.init.commands

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.AdminKey
import com.demo.chat.domain.knownkey.AnonymousKey
import com.demo.chat.service.composite.ChatMessageService
import org.springframework.context.annotation.Profile
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import java.util.function.Supplier

@Profile("shell")
@ShellComponent
class PubSubCommands<T>(
    private val messageService: ChatMessageService<T, String>,
    private val anonKey: Supplier<AnonymousKey<T>>,
    private val adminKey: Supplier<AdminKey<T>>,
    private val typeUtil: TypeUtil<T>
    ) {

    @ShellMethod("Send a Message")
    fun send(
        @ShellOption topicId: String,
        @ShellOption messageText: String
    ) {
        messageService
            .send(MessageSendRequest(messageText, adminKey.get().id, typeUtil.assignFrom(topicId)))
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