package com.demo.chat.persistence.cassandra.repository

import com.demo.chat.domain.Key
import com.demo.chat.persistence.cassandra.domain.ChatMessageById
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono

interface ChatMessageRepository<T : Any> : ChatMessageRepositoryCustom<T>, ReactiveCassandraRepository<ChatMessageById<T>, T> {
    fun findByKeyId(id: T): Mono<ChatMessageById<T>>
    @Suppress("unused")
    fun deleteByKeyId(msgId: T): Mono<Void>
}

interface ChatMessageRepositoryCustom<T> {
    fun rem(key: Key<T>): Mono<Void>
    fun add(msg: ChatMessageById<T>): Mono<Void>
}

@Suppress("unused")
class ChatMessageRepositoryCustomImpl<T>(val cassandra: ReactiveCassandraTemplate)
    : ChatMessageRepositoryCustom<T> {
    /**
     * `msg_time` is a clustering column, so an update must name it. The
     * removal reads the row first to get its time. See `CHAT-xcmpudyb`.
     */
    override fun rem(key: Key<T>): Mono<Void> =
            cassandra
                    .selectOne(Query.query(where("msg_id").`is`(key.id)), ChatMessageById::class.java)
                    .flatMap { row ->
                        cassandra.update(
                                Query.query(where("msg_id").`is`(key.id), where("msg_time").`is`(row.key.timestamp)),
                                Update.empty().set("visible", false),
                                ChatMessageById::class.java
                        )
                    }
                    .then()

    override fun add(msg: ChatMessageById<T>): Mono<Void> =
            cassandra
                    .insert(msg)
                    .then()
}