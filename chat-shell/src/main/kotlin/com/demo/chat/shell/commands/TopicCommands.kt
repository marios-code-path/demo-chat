package com.demo.chat.shell.commands

import com.demo.chat.config.client.rsocket.CoreClientsConfiguration
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.security.AuthorizationService
import org.springframework.context.annotation.Profile
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellOption
import reactor.core.publisher.Flux

@Profile("shell")
@ShellComponent
class TopicCommands<T>(
    private val topicService: ChatTopicService<T, String>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val serviceBeans: CoreClientsConfiguration<T, String, IndexSearchRequest>,
    private val typeUtil: TypeUtil<T>,
    private val keyGenerator: IKeyGenerator<T>,
    rootKeys: RootKeys<T>
) : CommandsUtil<T>(typeUtil, rootKeys) {

    fun topicToString(topic: MessageTopic<T>): String = "${topic.key.id} | ${topic.data}\n"

    @ShellMethod("show topics")
    fun showTopics(): String? = topicService
        .listRooms()
        .map(::topicToString)
        .reduce { t, u -> t + u }
        .block()

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
                            Key.funKey(keyGenerator.nextId()),
                            Key.funKey(identity),
                            topicKey,
                            "*",
                            Long.MAX_VALUE
                        ), true
                    )
            }
            .block()
    }

    @ShellMethod("Topic by Name")
    fun topicByName(
        @ShellOption(defaultValue = "_") userId: String,
        @ShellOption name: String
    ): String? = topicService
        .getRoomByName(ByStringRequest(name))
        .map(::topicToString)
        .block()

    @ShellMethod("Subscribe to a topic")
    fun join(
        @ShellOption(defaultValue = "_") userId: String,
        @ShellOption topicName: String
    ) = topicService
        .getRoomByName(ByStringRequest(topicName))
        .flatMap { topic ->
            topicService
                .joinRoom(
                    MembershipRequest(
                        identity(userId),
                        topic.key.id
                    )
                )
        }
        .block()

    @ShellMethod("unSubscribe to a topic")
    fun leave(
        @ShellOption(defaultValue = "_") userId: String,
        @ShellOption topicName: String
    ) = topicService
        .getRoomByName(ByStringRequest(topicName))
        .flatMap { topic ->
            topicService
                .leaveRoom(MembershipRequest(identity(userId), topic.key.id))
        }
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

    fun topicMembershipToString(membership: TopicMembership<T>): String =
        "${membership.member} | ${membership.memberOf}\n"

    fun topicMemberToString(member: TopicMember): String = "${member.uid} | ${member.handle} | ${member.imgUri}\n"

    @ShellMethod("Show Subscribers on a topic")
    fun listMembers(
        @ShellOption topicName: String
    ): String? = topicService
        .getRoomByName(ByStringRequest(topicName))
        .flatMap { topic -> topicService.roomMembers(ByIdRequest(topic.key.id)) }
        .flatMapMany { s -> Flux.fromIterable(s.members) }
        .map(::topicMemberToString)
        .reduce { t, u -> t + u }
        .block()

}