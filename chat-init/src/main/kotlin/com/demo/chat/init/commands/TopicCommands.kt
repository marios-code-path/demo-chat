package com.demo.chat.init.commands

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByNameRequest
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.deploy.client.consul.config.ServiceBeanConfiguration
import com.demo.chat.domain.*
import com.demo.chat.init.domain.AdminKey
import com.demo.chat.init.domain.AnonymousKey
import com.demo.chat.service.IKeyGenerator
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
    private val serviceBeans: ServiceBeanConfiguration<T, String, IndexSearchRequest>,
    private val typeUtil: TypeUtil<T>,
    private val anonKey: AnonymousKey<T>,
    private val adminKey: AdminKey<T>,
    private val keyGenerator: IKeyGenerator<T>
) {

    @ShellMethod("show topics")
    fun allTopics() = topicService
        .listRooms()
        .doOnNext { topic ->
            println("${topic.key.id} | ${topic.data}")
        }
        .blockLast()

    @ShellMethod("Create a topic")
    fun addTopic(
        @ShellOption userId: String,
        @ShellOption name: String
    ) = topicService
        .addRoom(ByNameRequest(name))
        .flatMap { topicKey ->
            authorizationService
                .authorize(
                    AuthMetadata.create(
                        Key.funKey(keyGenerator.nextKey()),
                        Key.funKey(typeUtil.fromString(userId)),
                        topicKey,
                        "*",
                        1
                    ), true
                )
        }
        .block()

    @ShellMethod("Subscribe to a topic")
    fun join(
        @ShellOption userId: T,
        @ShellOption topicId: T
    ) = topicService
        .joinRoom(MembershipRequest(userId, topicId))
        .block()


    @ShellMethod("Show what topics user is subscribed to")
    fun memberOf(
        @ShellOption userId: T
    ): String? = serviceBeans
        .pubsubClient()
        .getByUser(userId)
        .map(typeUtil::toString)
        .reduce { t, u -> "${t}\n${u}" }
        .block()

    @ShellMethod("Show Subscribers on a topic")
    fun listMembers(
        @ShellOption topicId: T
    ): TopicMemberships? = topicService
        .roomMembers(ByIdRequest(topicId))
        .block()
}