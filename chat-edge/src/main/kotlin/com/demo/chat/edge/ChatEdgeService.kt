package com.demo.chat.edge

import com.demo.chat.domain.*
import com.demo.chat.service.ChatService
import com.demo.chat.service.ChatTopicService
import reactor.core.publisher.Mono
import java.util.*

interface ChatUserEdge {
    fun createUser(name: String, handle: String): Mono<User<UserKey>>
    fun getUser(handle: String): Mono<User<UserKey>>

    // TODO WE DO NOT HAVE proper user authentication mechansism yet.. FYI
    fun createUserAuthentication(uid: UUID, password: String): Mono<Void>
    fun authenticateUser(name: String, password: String): Mono<User<UserKey>>
}

interface ChatRoomEdge {
    fun createRoom(name: String): Mono<Room<RoomKey>>
    fun roomInfo(roomId: UUID): Mono<RoomInfo>
    fun deleteRoom(roomId: UUID): Mono<Void>
}

interface ChatTopicEdge {
    fun subscribeToTopic(member: UUID, topic: UUID): Mono<Void>
    fun unSubscribeFromTopic(member: UUID, topic: UUID): Mono<Void>
    fun unSubscribeFromAllTopics(member: UUID): Mono<Void>
    fun kickallFromTopic(topic: UUID): Mono<Void>
}

class ChatEdgeService(topicService: ChatTopicService,
                      backendService: ChatService<Room<RoomKey>, User<UserKey>, Message<TextMessageKey, Any>>) {


}
