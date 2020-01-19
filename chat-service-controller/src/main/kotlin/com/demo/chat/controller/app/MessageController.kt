package com.demo.chat.controller.app

import com.demo.chat.MessageRequest
import com.demo.chat.MessageSendRequest
import com.demo.chat.MessagesRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.ChatTopicMessagingService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.MessageIndexService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class MessageController<T, V>(
        val messageIndex: IndexService<T, Message<T, V>, Map<String, T>>,
        val messagePersistence: PersistenceStore<T, Message<T, V>>,
        val topicMessaging: ChatTopicMessagingService<T, V>) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("message-listen-topic")
    fun byTopic(req: MessagesRequest<T>): Flux<out Message<T, V>> =
            Flux.concat(messageIndex
                    .findBy(mapOf(Pair(MessageIndexService.TOPIC, req.topicId)))
                    .collectList()
                    .flatMapMany { messageKeys ->
                        messagePersistence.byIds(messageKeys)
                    },
                    topicMessaging.receiveOn(req.topicId))

    @MessageMapping("message-by-id")
    fun getOne(req: MessageRequest<T>): Mono<out Message<T, V>> =
            messagePersistence
                    .get(Key.funKey(req.messageId))

// TODO : Generic form should  enable this to be unnecessary
//    @MessageMapping("text-message-send")
//    fun putTextMessage(req: TextMessageSend<T>): Mono<out MessageKey<T>> =
//            messagePersistence
//                    .key()
//                    .map { key ->
//                        MessageSendRequest(Message.create(
//                                MessageKey.create(key.id, req.topic, req.uid), req.text, true))
//                    }
//                    .flatMap { put ->
//                        add(put).map { put.msg.key } }

    // TODO currently we need to add more persistence support for non-text (whose sending non text messages by rsocket ?? )
    // TODO Needs to ensure KEY ID is set
    @MessageMapping("message-send")
    fun add(req: MessageSendRequest<T, V>): Mono<Void> {
        val publisher = messagePersistence
                .add(req.msg)
                .thenMany(messageIndex.add(req.msg))
                .then()

        return Flux
                .from(publisher)
                .then(topicMessaging.sendMessage(req.msg))
                .then()
    }
}