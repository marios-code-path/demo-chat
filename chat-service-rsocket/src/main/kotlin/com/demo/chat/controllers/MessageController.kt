package com.demo.chat.controllers

import com.demo.chat.MessageRequest
import com.demo.chat.MessagesRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import com.demo.chat.service.TextMessagePersistence
import com.demo.chat.service.ChatTopicService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Controller
class MessageController(
        val dataPersistence: TextMessagePersistence<out Message<TopicMessageKey, Any>, TopicMessageKey>,
        val topicService: ChatTopicService) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("message-list-topic")
    fun getMessagesForTopic(req: MessagesRequest): Flux<out Message<TopicMessageKey, Any>> =
            dataPersistence
                    .getAll(req.topicId)
                    .thenMany(topicService.receiveOn(req.topicId))

    @MessageMapping("message-msg-id")
    fun getMessage(req: MessageRequest): Mono<out Message<TopicMessageKey, Any>> =
            dataPersistence
                    .getById(req.messageId)
}