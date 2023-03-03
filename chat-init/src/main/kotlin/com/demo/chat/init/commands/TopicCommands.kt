package com.demo.chat.init.commands

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.config.client.rsocket.CoreClientsConfiguration
import com.demo.chat.domain.*
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.context.annotation.Profile
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption

@Profile("shell")
@ShellComponent
class TopicCommands<T>(
    private val topicService: ChatTopicService<T, String>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>, AuthMetadata<T>>,
    private val serviceBeans: CoreClientsConfiguration<T, String, IndexSearchRequest>,
    private val typeUtil: TypeUtil<T>,
    private val keyGenerator: IKeyGenerator<T>
) : CommandsUtil<T>(typeUtil){

    @ShellMethod("show topics")
    fun showTopics() = topicService
        .listRooms()
        .doOnNext { topic ->
            println("${topic.key.id} | ${topic.data}")
        }
        .blockLast()

    @ShellMethod("Create a topic")
    fun addTopic(
        @ShellOption(defaultValue = "_") userId: String,
        @ShellOption name: String
    ) {
        val identity = identity(userId)

        topicService
            .addRoom(ByStringRequest(name))
            .flatMap { topicKey ->
                authorizationService
                    .authorize(
                        AuthMetadata.create(
                            Key.funKey(keyGenerator.nextKey()),
                            Key.funKey(identity),
                            topicKey,
                            "*",
                            Long.MAX_VALUE
                        ), true
                    )
            }
            .block()
    }

    @ShellMethod("Subscribe to a topic")
    fun join(
        @ShellOption(defaultValue = "_") userId: String,
        @ShellOption topicId: String
    ) = topicService
        .joinRoom(MembershipRequest(identity(userId), typeUtil.assignFrom(topicId)))
        .block()

    @ShellMethod("unSubscribe to a topic")
    fun leave(
        @ShellOption(defaultValue = "_") userId: String,
        @ShellOption topicId: String
    ) = topicService
        .leaveRoom(MembershipRequest(identity(userId), typeUtil.assignFrom(topicId)))
        .block()

    @ShellMethod("Show what topics user is subscribed to")
    fun memberOf(
        @ShellOption(defaultValue = "_") userId: String,
    ): String? = serviceBeans
        .pubsubClient()
        .getByUser(identity(userId))
        .map(typeUtil::toString)
        .reduce { t, u -> "${t}\n${u}" }
        .block()

    @ShellMethod("Show Subscribers on a topic")
    fun listMembers(
        @ShellOption topicId: String
    ): TopicMemberships? {
        return topicService
            .roomMembers(ByIdRequest(typeUtil.assignFrom(topicId)))
            .block()
    }
}