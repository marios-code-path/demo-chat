package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.Message
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.TextMessageKey
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import com.demo.chat.repository.cassandra.ChatMessageRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

open class TextMessagePersistenceCassandra(private val messageRepo: ChatMessageRepository,
                                           private val messageByTopicRepo: ChatMessageByTopicRepository)
    : TextMessagePersistence<Message<TextMessageKey, Any>, TextMessageKey> {
    override fun key(uid: UUID, roomId: UUID): Mono<out TextMessageKey> =
            Mono.just(TextMessageKey.create(UUIDs.timeBased(), roomId, uid))

    override fun rem(key: TextMessageKey): Mono<Void> = messageRepo.rem(key)

    override fun getById(id: UUID): Mono<TextMessage> = messageRepo.findByKeyMsgId(id)

    override fun getAll(roomId: UUID): Flux<TextMessage> = messageByTopicRepo.findByKeyTopicId(roomId)

    override fun add(key: TextMessageKey, messageText: String): Mono<Void> = messageRepo.add(TextMessage.create(TextMessageKey.create(
            key.msgId,
            key.userId,
            key.topicId),
            messageText,
            true))
}