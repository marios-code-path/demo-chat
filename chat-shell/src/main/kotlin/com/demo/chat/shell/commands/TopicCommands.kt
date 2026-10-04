package com.demo.chat.shell.commands

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.CoreServices
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatTopicService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Profile("shell")
@Component
class TopicCommands<T : Any>(
    private val coreServices: CoreServices<T, String, IndexSearchRequest>,
    private val compositeServices: CompositeServiceBeans<T, String>,
    private val typeUtil: TypeUtil<T>,
    private val rootKeys: RootKeys<T>,
    private val rooms: ShellRooms<T>,
) : CommandsUtil<T>(typeUtil, rootKeys) {

    private val verifier = KeyVerifier(coreServices.keyService(), rootKeys)

    fun topicToString(topic: MessageTopic<T>): String = "${topic.key.id} | ${topic.data}\n"

    private val topicService: ChatTopicService<T, String> = compositeServices.topicService()
    fun showTopics(): String? = topicService
        .listRooms()
        .map(::topicToString)
        .reduce { t, u -> t + u }
        .block()
    /** Answers the key of the new room, so the command can confirm it. */
    fun addTopic(
        userId: String,
        name: String
    ): Key<T>? {
        val identity = identity(userId)

        // The creator resolves in USER through the server registry before the
        // room exists. An unknown creator creates no room. No call blocks
        // inside the chain, because a remote answer runs it on a Netty thread.
        // The server writes the ownership row, and it writes it at room
        // creation. The shell writes none, because `*` is singular per target
        // and one writer is required. See CHAT-zhjltbky.
        return verifier.resolve(identity, ChatDomain.USER)
            .flatMap { topicService.addRoom(ByStringRequest(name)) }
            .block()
    }
    fun topicByName(
        userId: String,
        name: String
    ): String? = topicService
        .getRoomByName(ByStringRequest(name))
        .map(::topicToString)
        .block()
    /** [topic] is a room name or a room id. See `CHAT-scoizkpm`. */
    fun join(
        userId: String,
        topic: String
    ) = topicService
        .joinRoom(MembershipRequest(identity(userId), rooms.idOf(topic)))
        .block()
    /** [topic] is a room name or a room id. */
    fun leave(
        userId: String,
        topic: String
    ) = topicService
        .leaveRoom(MembershipRequest(identity(userId), rooms.idOf(topic)))
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
    /** [topic] is a room name or a room id. */
    fun listMembers(
        topic: String
    ): String? = topicService
        .roomMembers(ByIdRequest(rooms.idOf(topic)))
        .flatMapMany { s -> Flux.fromIterable(s.members) }
        .map(::topicMemberToString)
        .reduce { t, u -> t + u }
        .block()

}