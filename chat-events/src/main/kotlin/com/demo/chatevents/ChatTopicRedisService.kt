package com.demo.chatevents

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
import reactor.core.publisher.DirectProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

class ChatTopicRedisService : ChatTopicService, ChatTopicServiceAdmin {
    override fun subscribeToTopic(member: UUID, topic: UUID): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unSubscribeFromTopic(member: UUID, topic: UUID): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unSubscribeFromAllTopics(member: UUID): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun kickallFromTopic(topic: UUID): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sendMessageToTopic(message: Message<MessageKey, Any>): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun receiveTopicEvents(topic: UUID): Flux<out Message<MessageKey, Any>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getMemberTopics(uid: UUID): List<UUID> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getTopicMembers(uid: UUID): List<UUID> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun closeTopic(topic: UUID): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getTopicProcessor(topicId: UUID): DirectProcessor<out Message<MessageKey, Any>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}