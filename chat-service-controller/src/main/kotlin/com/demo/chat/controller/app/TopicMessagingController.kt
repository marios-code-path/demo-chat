package com.demo.chat.controller.app

import com.demo.chat.MessageRequest
import com.demo.chat.MessageSendRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.ChatTopicMessagingService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.MessagePersistence
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class TopicMessagingController<T, V>(
        private val messageIndex: MessageIndexService<T, V>,
        private val messagePersistence: MessagePersistence<T, V>,
        private val topicMessaging: ChatTopicMessagingService<T, V>) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("message-listen-topic")
    fun byTopic(req: MessageRequest<T>): Flux<out Message<T, V>> =
            Flux.concat(messageIndex
                    .findBy(mapOf(Pair(MessageIndexService.TOPIC, req.id)))
                    .collectList()
                    .flatMapMany { messageKeys ->
                        messagePersistence.byIds(messageKeys)
                    },
                    topicMessaging.receiveOn(req.id))

    @MessageMapping("message-by-id")
    fun messageById(req: MessageRequest<T>): Mono<out Message<T, V>> =
            messagePersistence
                    .get(Key.funKey(req.id))

    @MessageMapping("message-send")
    fun send(req: MessageSendRequest<T, V>): Mono<Void> {
        val sending: (T) -> Message<T, V> = {
            Message.create(MessageKey.create(it, req.from, req.dest), req.msg, true)
        }

        return messagePersistence
                .key()
                .flatMap {
                    Flux.concat(
                            messagePersistence.add(sending(it.id)),
                            messageIndex.add(sending(it.id)),
                            topicMessaging.sendMessage(sending(it.id))
                    )
                            .then()
                }
    }
}