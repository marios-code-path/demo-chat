package com.demo.chat.repository.cassandra

import com.demo.chat.domain.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatMessageUserRepository : ReactiveCassandraRepository<ChatMessageUser, UUID> {
    fun findByKeyUserId(userId: UUID) : Flux<ChatMessageUser>
}

interface ChatMessageRoomRepository : ReactiveCassandraRepository<ChatMessageRoom, UUID> {
    fun findByKeyRoomId(roomId: UUID) : Flux<ChatMessageRoom>
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
                .insert(ChatMessageUser(
                        ChatMessageUserKey(
                                msg.key.id,
                                msg.key.userId,
                                msg.key.roomId,
                                msg.key.timestamp
                        ),
                        msg.value,
                        msg.visible

                ))
                .insert(ChatMessageRoom(
                        ChatMessageRoomKey(
                                msg.key.id,
                                msg.key.userId,
                                msg.key.roomId,
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