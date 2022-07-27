package com.demo.chat.init

import com.demo.chat.deploy.client.consul.config.ServiceBeanConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TypeUtil
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption

@ShellComponent
class PubSubCommands<T>(
    private val serviceBeans: ServiceBeanConfiguration<T, String, IndexSearchRequest>,
    private val typeUtil: TypeUtil<T>,
    private val anonKey: AnonymousKey<T>,
    private val adminKey: AdminKey<T>,
) {

    @ShellMethod("Send a Message")
    fun send(
        @ShellOption topicId: T,
        @ShellOption messageText: String
    ) {
        val keySvc = serviceBeans.keyClient()
        keySvc.key(Message::class.java).flatMap { messageId ->
            serviceBeans.pubsubClient()
                .sendMessage(
                    Message.create(
                        MessageKey.Factory.create(messageId.id, adminKey.id, topicId),
                        messageText,
                        true
                    )
                )
        }
            .block()

        // ERROR HANDLING
    }

    @ShellMethod("Subscribe to a topic")
    fun subscribe(
        @ShellOption userId: T,
        @ShellOption topicId: T
    ) = serviceBeans
        .pubsubClient()
        .subscribe(userId, topicId)
        .block()


    @ShellMethod("Show what topics user is subscribed to")
    fun subsribedTo(
        @ShellOption userId: T
    ): String? = serviceBeans
        .pubsubClient()
        .getByUser(userId)
        .map(typeUtil::toString)
        .reduce { t, u -> "${t}\n${u}" }
        .block()

    @ShellMethod("Show Subscribers on a topic")
    fun subscribers(
        @ShellOption topicId: T
    ): String? = serviceBeans
            .pubsubClient()
            .getUsersBy(topicId)
            .map {
                typeUtil.toString(it)
            }
            .reduce { t, u -> "$t\n$u" }
            .block()

    @ShellMethod("Listen to a topic")
    fun listenSubscription(
        @ShellOption userId: T,
        @ShellOption topicId: T
    ) = serviceBeans
        .pubsubClient()
        .listenTo(topicId)
        .doOnNext { message ->
            println("Message: ${message.key.from} : ${message.data}\n")
        }
        .subscribe()

}