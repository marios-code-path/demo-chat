package com.demo.chat.init.commands

import com.demo.chat.ByIdRequest
import com.demo.chat.MembershipRequest
import com.demo.chat.MessageSendRequest
import com.demo.chat.deploy.client.consul.config.ServiceBeanConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TopicMemberships
import com.demo.chat.domain.TypeUtil
import com.demo.chat.init.domain.AdminKey
import com.demo.chat.init.domain.AnonymousKey
import com.demo.chat.service.edge.ChatMessageService
import com.demo.chat.service.edge.ChatTopicService
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption

@ShellComponent
class PubSubCommands<T>(
    private val messageService: ChatMessageService<T, String>,
    private val topicService: ChatTopicService<T, String>,
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
        messageService
            .send(MessageSendRequest(messageText, adminKey.id, topicId))
            .block()
    }

    @ShellMethod("Subscribe to a topic")
    fun subscribe(
        @ShellOption userId: T,
        @ShellOption topicId: T
    ) = topicService
        .joinRoom(MembershipRequest(userId, topicId))
        .block()


    @ShellMethod("Show what topics user is subscribed to")
    fun userSubscriptions(
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
    ): TopicMemberships? = topicService.roomMembers(ByIdRequest(topicId)).block()

    @ShellMethod("Listen to a topic")
    fun listen(
        @ShellOption userId: T,
        @ShellOption topicId: T
    ) = messageService.listenTopic(ByIdRequest(topicId))
        .doOnNext { message ->
            println("Message: ${message.key.from} : ${message.data}\n")
        }
        .subscribe()

}