package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.ChatMessage
import com.demo.chat.domain.ChatMessageKey
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TextMessage
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

open class ChatMessageServiceCassandra(private val messageRepo: ChatMessageRepository,
                                       private val messageByTopicRepo: ChatMessageByTopicRepository)
    : ChatMessageService<TextMessage, MessageKey> {
    override fun getMessage(id: UUID): Mono<ChatMessage> =
            messageRepo
                    .findByKeyId(id)

    override fun getTopicMessages(roomId: UUID): Flux<ChatMessage> = messageByTopicRepo.findByKeyTopicId(roomId)
            .map {
                ChatMessage(
                        ChatMessageKey(
                                it.key.id,
                                it.key.userId,
                                it.key.topicId,
                                it.key.timestamp
                        ),
                        it.value,
                        it.visible
                )
            }

    override fun storeMessage(uid: UUID, roomId: UUID, messageText: String): Mono<MessageKey> =
            messageRepo
                    .saveMessage(ChatMessage(ChatMessageKey(UUIDs.timeBased(),
                            uid,
                            roomId,
                            Instant.now()),
                            messageText,
                            true))
                    .map {
                        it.key
                    }

}