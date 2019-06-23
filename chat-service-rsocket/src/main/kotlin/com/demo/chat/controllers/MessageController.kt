package com.demo.chat.controllers

import com.demo.chat.MessageRequest
import com.demo.chat.MessagesRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import com.demo.chat.service.ChatMessageService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Controller
class MessageController(val topicMessageService: ChatMessageService<out Message<TopicMessageKey, Any>, TopicMessageKey>) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("message-list-topic")
    fun getMessagesForTopic(req: MessagesRequest): Flux<out Message<TopicMessageKey, Any>> =
            topicMessageService
                    .getTopicMessages(req.topicId)

    @MessageMapping("message-msgId")
    fun getMessage(req: MessageRequest): Mono<out Message<TopicMessageKey, Any>> =
            topicMessageService
                    .getMessage(req.messageId)
}