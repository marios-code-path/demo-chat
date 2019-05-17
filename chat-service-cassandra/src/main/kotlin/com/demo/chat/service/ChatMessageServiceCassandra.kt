package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.ChatMessage
import com.demo.chat.domain.ChatMessageKey
import com.demo.chat.domain.MessageKey
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.repository.cassandra.ChatMessageRoomRepository
import com.demo.chat.service.ChatMessageService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

class ChatMessageServiceCassandra(val messageRepo: ChatMessageRepository,
                                  val messageRoomRepo: ChatMessageRoomRepository) : ChatMessageService<MessageKey, ChatMessage> {
    override fun getMessage(id: UUID): Mono<ChatMessage> =
            messageRepo
                    .findByKeyId(id)

    override fun getTopicMessages(roomId: UUID): Flux<ChatMessage> = messageRoomRepo.findByKeyRoomId(roomId)
            .map {
                ChatMessage(
                        ChatMessageKey(
                                it.key.id,
                                it.key.userId,
                                it.key.roomId,
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