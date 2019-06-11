package com.demo.chat.service

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatTopicService {
    fun subscribeToTopic(member: UUID, topic: UUID): Mono<Void>
    fun unSubscribeFromTopic(member: UUID, topic: UUID): Mono<Void>
    fun unSubscribeFromAllTopics(member: UUID): Mono<Void> // then closes
    fun kickallFromTopic(topic: UUID): Mono<Void>
    fun sendMessageToTopic(message: Message<MessageKey, Any>): Mono<Void>
    fun receiveTopicEvents(topic: UUID): Flux<out Message<MessageKey, Any>>
    fun getMemberTopics(uid: UUID): List<UUID>
    fun getTopicMembers(uid: UUID): List<UUID>
    fun closeTopic(topic: UUID): Mono<Void>
}

interface ChatTopicServiceAdmin {
    fun getTopicProcessor(topicId: UUID): DirectProcessor<out Message<MessageKey, Any>>

}
