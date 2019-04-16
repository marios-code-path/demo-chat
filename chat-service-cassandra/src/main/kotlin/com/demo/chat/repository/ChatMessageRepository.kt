package com.demo.chat.repository

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import org.slf4j.LoggerFactory
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

interface ChatMessageRepository : ChatMessageRepositoryCustom, ReactiveCassandraRepository<ChatMessage, ChatMessageKey> {
    fun findByKeyId(id: UUID) : Mono<ChatMessage>
}

interface ChatMessageRepositoryCustom {
    fun saveMessages(msgStream: Flux<ChatMessage>): Flux<ChatMessage>
}

class ChatMessageRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate)
    : ChatMessageRepositoryCustom {
    val logger = LoggerFactory.getLogger("CASSANDRALOGGER")
    override fun saveMessages(msgStream: Flux<ChatMessage>): Flux<ChatMessage> =
            Flux.from(msgStream)
                    .flatMap {
                        cassandra
                                .batchOps()
                                .insert(it)
                                .insert(ChatMessageUser(
                                        ChatMessageUserKey(
                                                it.key.id,
                                                it.key.userId,
                                                it.key.roomId,
                                                it.key.timestamp
                                        ),
                                        it.text,
                                        it.visible

                                ))
                                .insert(ChatMessageRoom(
                                        ChatMessageRoomKey(
                                                it.key.id,
                                                it.key.userId,
                                                it.key.roomId,
                                                it.key.timestamp
                                        ),
                                        it.text,
                                        it.visible
                                ))
                                .execute()
                    }
                    .thenMany(msgStream)

}