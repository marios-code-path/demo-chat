package com.demo.chat.shell.commands

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.Admin
import com.demo.chat.domain.knownkey.Anon
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
    private val typeUtil: TypeUtil<T>
    ) : CommandsUtil<T>(typeUtil) {

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