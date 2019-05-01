package com.demo.chat.edge

import com.demo.chat.domain.*
import com.demo.chat.service.ChatService
import com.demo.chat.service.ChatTopicService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatUserEdge {
    fun createUser(name: String, handle: String): Mono<UserKey>
    fun getUser(handle: String): Mono<UserKey>

    // TODO WE DO NOT HAVE proper user authentication mechanism yet.. FYI
    fun createUserAuthentication(uid: UUID, password: String): Mono<Void>
    fun authenticateUser(name: String, password: String): Mono<UserKey>
}

interface ChatRoomEdge {
    fun createRoom(name: String): Mono<RoomKey>
    fun roomInfo(roomId: UUID): Mono<RoomInfo>
    fun roomMembers(roomId: UUID): Mono<RoomMemberships>
    fun deleteRoom(roomId: UUID): Mono<Void>
}

interface ChatTopicEdge {
    fun subscribeToTopic(member: UUID, topic: UUID): Mono<Void>
    fun unSubscribeFromTopic(member: UUID, topic: UUID): Mono<Void>
    fun closeTopic(topic: UUID): Mono<Void>
    fun unSubscribeFromAllTopics(member: UUID): Mono<Void> // then closes
    fun kickallFromTopic(topic: UUID): Mono<Void>

    fun receiveTopicEvents(topic: UUID): Flux<Message<MessageKey, Any>>
}

class ChatEdgeService(topicService: ChatTopicService,
                      backendService: ChatService<Room<RoomKey>, User<UserKey>, Message<TextMessageKey, Any>>)
{

}