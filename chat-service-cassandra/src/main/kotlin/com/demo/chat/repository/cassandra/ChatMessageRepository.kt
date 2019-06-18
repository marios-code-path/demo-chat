package com.demo.chat.repository.cassandra

import com.demo.chat.domain.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatMessageByUserRepository : ReactiveCassandraRepository<ChatMessageByUser, UUID> {
    fun findByKeyUserId(userId: UUID) : Flux<ChatMessageByUser>
}

interface ChatMessageByTopicRepository : ReactiveCassandraRepository<ChatMessageByTopic, UUID> {
    fun findByKeyTopicId(topicId: UUID) : Flux<ChatMessageByTopic>
}

interface ChatMessageRepository : ChatMessageRepositoryCustom, ReactiveCassandraRepository<ChatMessage, UUID> {
    fun findByKeyId(id: UUID) : Mono<ChatMessage>
}

interface ChatMessageRepositoryCustom {
    fun saveMessage(msg: ChatMessage): Mono<ChatMessage>
    fun saveMessages(msgStream: Flux<ChatMessage>): Flux<ChatMessage>
}

class ChatMessageRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate)
    : ChatMessageRepositoryCustom {

    override fun saveMessage(msg: ChatMessage): Mono<ChatMessage> =
        cassandra
                .batchOps()
                .insert(msg)
                .insert(ChatMessageByUser(
                        ChatMessageByUserKey(
                                msg.key.id,
                                msg.key.userId,
                                msg.key.topicId,
                                msg.key.timestamp
                        ),
                        msg.value,
                        msg.visible

                ))
                .insert(ChatMessageByTopic(
                        ChatMessageByTopicKey(
                                msg.key.id,
                                msg.key.userId,
                                msg.key.topicId,
                                msg.key.timestamp
                        ),
                        msg.value,
                        msg.visible
                ))
                .execute()
                .thenReturn(msg)

    override fun saveMessages(msgStream: Flux<ChatMessage>): Flux<ChatMessage> =
            Flux.from(msgStream)
                    .flatMap(this::saveMessage)
}