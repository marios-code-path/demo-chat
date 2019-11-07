package com.demo.chat.controllers

import com.demo.chat.MessageRequest
import com.demo.chat.MessageSendRequest
import com.demo.chat.MessagesRequest
import com.demo.chat.TextMessageSend
import com.demo.chat.domain.*
import com.demo.chat.service.ChatMessageIndexService
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
        val messageIndex: ChatMessageIndexService,
        val messagePersistence: TextMessagePersistence,
        val topicService: ChatTopicService) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("message-listen-topic")
    fun byTopic(req: MessagesRequest): Flux<out Message<TopicMessageKey, Any>> =
            Flux.concat(messageIndex
                    .findBy(mapOf(Pair(ChatMessageIndexService.TOPIC, req.topicId.toString())))
                    .collectList()
                    .flatMapMany { messageKeys ->
                        messagePersistence.byIds(messageKeys)
                    },
                    topicService.receiveOn(req.topicId))

    @MessageMapping("message-by-id")
    fun getOne(req: MessageRequest): Mono<out Message<TopicMessageKey, Any>> =
            messagePersistence
                    .get(EventKey.create(req.messageId))

    @MessageMapping("text-message-send")
    fun putTextMessage(req: TextMessageSend): Mono<out TopicMessageKey> =
            messagePersistence
                    .key()
                    .map { key ->
                        MessageSendRequest(TextMessage.create(
                                TextMessageKey.create(key, req.topic, req.uid), req.text, true))
                    }
                    .flatMap { put -> add(put).map { put.msg.key } }

    // TODO currently we need to add more persistence support for non-text (whose sending non text messages by rsocket ?? )
    // TODO Needs to ensure KEY ID is set
    @MessageMapping("message-send")
    fun add(req: MessageSendRequest): Mono<Void> {
        val publisher = when (req.msg) {
            is TextMessage -> messagePersistence
                    .add(req.msg)
                    .thenMany(messageIndex.add(req.msg, mapOf()))
                    .then()
            else -> Mono.empty()
        }

        return Flux.from(publisher)
                .then(topicService.sendMessage(req.msg))
                .then()
    }

}