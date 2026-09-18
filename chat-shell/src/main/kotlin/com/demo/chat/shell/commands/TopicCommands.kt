package com.demo.chat.shell.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.CoreServices
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.security.AuthorizationService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Profile("shell")
@Component
class TopicCommands<T : Any>(
    private val coreServices: CoreServices<T, String, IndexSearchRequest>,
    private val compositeServices: CompositeServiceBeans<T, String>,
    private val authorizationService: AuthorizationService<T, AuthMetadata<T>>,
    private val typeUtil: TypeUtil<T>,
    rootKeys: RootKeys<T>
) : CommandsUtil<T>(typeUtil, rootKeys) {

    fun topicToString(topic: MessageTopic<T>): String = "${topic.key.id} | ${topic.data}\n"

    private val topicService: ChatTopicService<T, String> = compositeServices.topicService()
    fun showTopics(): String? = topicService
        .listRooms()
        .map(::topicToString)
        .reduce { t, u -> t + u }
        .block()
    fun addTopic(
        userId: String,
        name: String
    ) {
        val identity = identity(userId)

        topicService
            .addRoom(ByStringRequest(name))
            .flatMap { topicKey ->
                authorizationService
                    .authorize(
                        AuthMetadata.create(
                            Key.emptyKey(typeUtil.empty()),
                            Key.funKey(identity),
                            topicKey,
                            "*",
                            Long.MAX_VALUE
                        ), true
                    )
            }
            .block()
    }
    fun topicByName(
        userId: String,
        name: String
    ): String? = topicService
        .getRoomByName(ByStringRequest(name))
        .map(::topicToString)
        .block()
    fun join(
        userId: String,
        topicName: String
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
    fun leave(
        userId: String,
        topicName: String
    ) = topicService
        .getRoomByName(ByStringRequest(topicName))
        .flatMap { topic ->
            topicService
                .leaveRoom(MembershipRequest(identity(userId), topic.key.id))
        }
        .block()
    fun memberOf(
        userId: String,
    ): String? = coreServices
        .pubSubService()
        .getByUser(identity(userId))
        .map(typeUtil::toString)
        .reduce { t, u -> "${t}\n${u}" }
        .block()

    fun topicMembershipToString(membership: TopicMembership<T>): String =
        "${membership.member} | ${membership.memberOf}\n"

    fun topicMemberToString(member: TopicMember): String = "${member.uid} | ${member.handle} | ${member.imgUri}\n"
    fun listMembers(
        topicName: String
    ): String? = topicService
        .getRoomByName(ByStringRequest(topicName))
        .flatMap { topic -> topicService.roomMembers(ByIdRequest(topic.key.id)) }
        .flatMapMany { s -> Flux.fromIterable(s.members) }
        .map(::topicMemberToString)
        .reduce { t, u -> t + u }
        .block()

}