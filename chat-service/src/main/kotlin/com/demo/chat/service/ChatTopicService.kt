package com.demo.chat.service

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*


interface ChatTopicService {
    fun getTopicStream(topicId: UUID): Flux<Message<MessageKey, Any>>
    fun getTopicMembers(topic: UUID): List<UUID>
    fun getMemberTopics(uid: UUID): List<UUID>
    fun subscribeMember(uid: UUID, topicId: UUID): Mono<Void>
    fun unsubscribeMember(uid: UUID, topicId: UUID): Mono<Void>
    fun unsubscribeMemberAllTopics(uid: UUID): Mono<Void>
    fun unsubscribeTopicAllMembers(feedId: UUID): Mono<Void>
    fun sendMessageToTopic(message: Message<MessageKey, Any>): Mono<Void>
}

interface ChatTopicServiceAdmin {
    fun getTopicProcessor(topicId: UUID): DirectProcessor<Message<MessageKey, Any>>
}
