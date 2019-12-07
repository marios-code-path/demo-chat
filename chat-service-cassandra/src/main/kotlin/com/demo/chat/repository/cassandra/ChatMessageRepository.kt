package com.demo.chat.repository.cassandra

import com.demo.chat.domain.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatMessageByUserRepository : ReactiveCassandraRepository<ChatMessageByUser, UUID> {
    fun findByKeyUserId(userId: UUID): Flux<ChatMessageByUser>
}

interface ChatMessageByTopicRepository : ReactiveCassandraRepository<ChatMessageByTopic, UUID> {
    fun findByKeyTopicId(topicId: UUID): Flux<ChatMessageByTopic>
}

interface ChatMessageRepository : ChatMessageRepositoryCustom, ReactiveCassandraRepository<ChatMessageById, UUID> {
    fun findByKeyId(id: UUID): Mono<ChatMessageById>
}

interface ChatMessageRepositoryCustom {
    fun rem(key: UUIDKey): Mono<Void>
    fun add(msg: TextMessage): Mono<Void>
}

class ChatMessageRepositoryCustomImpl(val cassandra: ReactiveCassandraTemplate)
    : ChatMessageRepositoryCustom {
    override fun rem(key: UUIDKey): Mono<Void> =
            cassandra
                    .update(Query.query(where("msg_id").`is`(key.id)),
                            Update.empty().set("visible", false),
                            ChatMessageById::class.java
                    )
                    .then()

    override fun add(msg: TextMessage): Mono<Void> =
            cassandra
                    .insert(ChatMessageById(
                            ChatMessageByIdKey(
                                    msg.key.id,
                                    msg.key.userId,
                                    msg.key.topicId,
                                    msg.key.timestamp
                            ),
                            msg.value,
                            msg.visible
                    ))
                    .then()

}