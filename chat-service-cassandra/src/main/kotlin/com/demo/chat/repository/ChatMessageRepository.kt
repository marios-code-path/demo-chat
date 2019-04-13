package com.demo.chat.repository

import com.demo.chat.domain.ChatMessage
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.*

interface ChatMessageRepository : ReactiveCassandraRepository<ChatMessage, UUID> {

    fun findByKeyRoomId(roomId: UUID): Flux<ChatMessage>

    fun findByKeyUserId(userId: UUID): Flux<ChatMessage>

    fun findByKeyRoomIdAndKeyTimestampAfter(roomId: UUID, since: Instant): Flux<ChatMessage>
}
