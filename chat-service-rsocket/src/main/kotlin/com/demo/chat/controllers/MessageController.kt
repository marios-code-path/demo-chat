package com.demo.chat.controllers

import com.demo.chat.MessageRequest
import com.demo.chat.MessageSendRequest
import com.demo.chat.MessagesRequest
import com.demo.chat.TextMessageSend
import com.demo.chat.domain.Message
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.TextMessageKey
import com.demo.chat.domain.TopicMessageKey
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.TextMessagePersistence
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Controller
class MessageController(
        val dataPersistence: TextMessagePersistence<out TextMessage, TextMessageKey>,
        val topicService: ChatTopicService) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("message-listen-topic")
    fun byTopic(req: MessagesRequest): Flux<out Message<TopicMessageKey, Any>> =
            Flux.concat(dataPersistence
                    .getAll(req.topicId),
                    topicService.receiveOn(req.topicId))

    @MessageMapping("message-by-id")
    fun getOne(req: MessageRequest): Mono<out Message<TopicMessageKey, Any>> =
            dataPersistence
                    .getById(req.messageId)

    @MessageMapping("text-message-send")
    fun putTextMessage(req: TextMessageSend): Mono<out TopicMessageKey> =
            dataPersistence
                    .key(req.uid, req.topic)
                    .map { key -> MessageSendRequest(TextMessage.create(key, req.text, true)) }
                    .flatMap { put -> add(put).map { put.msg.key } }

    // TODO currently we need to add more persistence support for non-text (whose sending non text messages by rsocket ?? )
    @MessageMapping("message-send")
    fun add(req: MessageSendRequest): Mono<Void> {
        val publisher = when (req.msg) {
            is TextMessage -> dataPersistence.add(req.msg.key, req.msg.value)
            else -> Mono.empty()
        }

        return Flux.from(publisher)
                .then(topicService.sendMessage(req.msg))
                .then()
    }

}