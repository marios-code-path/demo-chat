package com.demo.chat.service

import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatTopicService {
    fun createTopic(topicId: UUID): Mono<Void>
    fun subscribeToTopic(member: UUID, topic: UUID): Mono<Void>
    fun unSubscribeFromTopic(member: UUID, topic: UUID): Mono<Void>
    fun unSubscribeFromAllTopics(member: UUID): Mono<Void> // then closes
    fun kickallFromTopic(topic: UUID): Mono<Void>
    fun sendMessageToTopic(topicMessage: Message<TopicMessageKey, Any>): Mono<Void>
    fun receiveEvents(topic: UUID): Flux<out Message<TopicMessageKey, Any>>
    fun getMemberTopics(uid: UUID): Flux<UUID>
    fun getTopicMembers(uid: UUID): Flux<UUID>
    fun closeTopic(topic: UUID): Mono<Void>
    fun topicExists(topic: UUID): Mono<Boolean>
}

interface ChatTopicServiceAdmin {
    fun getTopicProcessor(topicId: UUID): DirectProcessor<out Message<TopicMessageKey, Any>>

}
