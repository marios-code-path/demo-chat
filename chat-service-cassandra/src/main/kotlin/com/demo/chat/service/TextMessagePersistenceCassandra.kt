package com.demo.chat.service

import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.TextMessageKey
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import com.demo.chat.repository.cassandra.ChatMessageRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

open class TextMessagePersistenceCassandra(private val keyService: KeyService,
                                           private val messageRepo: ChatMessageRepository,
                                           private val messageByTopicRepo: ChatMessageByTopicRepository)
    : TextMessagePersistence<TextMessage, TextMessageKey> {

    override fun key(uid: UUID, roomId: UUID): Mono<TextMessageKey> =
            keyService.key(TextMessageKey::class.java) { TextMessageKey.create(it.id, roomId, uid) }

    override fun rem(key: TextMessageKey): Mono<Void> =
            messageRepo.rem(key)

    override fun getById(id: UUID): Mono<out TextMessage> =
            messageRepo.findByKeyMsgId(id)

    override fun getAll(roomId: UUID): Flux<out TextMessage> =
            messageByTopicRepo.findByKeyTopicId(roomId)

    override fun add(key: TextMessageKey, messageText: String): Mono<Void> =
            messageRepo.add(TextMessage.create(TextMessageKey.create(
                    key.msgId,
                    key.userId,
                    key.topicId),
                    messageText,
                    true))
}