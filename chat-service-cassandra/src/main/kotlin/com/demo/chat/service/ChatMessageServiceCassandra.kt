package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

open class ChatMessageServiceCassandra(private val messageRepo: ChatMessageRepository,
                                       private val messageByTopicRepo: ChatMessageByTopicRepository)
    : ChatMessageService<Message<TextMessageKey, Any>, TextMessageKey> {
    override fun getMessage(id: UUID): Mono<out Message<TextMessageKey, Any>> = messageRepo
                    .findByKeyId(id)

    override fun getTopicMessages(roomId: UUID): Flux<out Message<TextMessageKey, Any>> = messageByTopicRepo
            .findByKeyTopicId(roomId)

    override fun storeMessage(uid: UUID, roomId: UUID, messageText: String): Mono<out TextMessageKey> = messageRepo
                    .saveMessage(ChatMessageById(ChatMessageByIdKey(UUIDs.timeBased(),
                            uid,
                            roomId,
                            Instant.now()),
                            messageText,
                            true))
                    .map {
                        it.key
                    }

}