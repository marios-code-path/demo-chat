package com.demo.chat.repository.cassandra

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.cassandra.ChatMessageById
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono

interface ChatMessageRepository<T> : ChatMessageRepositoryCustom<T>, ReactiveCassandraRepository<ChatMessageById<T>, T> {
    fun findByKeyId(id: T): Mono<ChatMessageById<T>>
    @Suppress("unused")
    fun deleteByKeyId(msgId: T): Mono<Void>
}

interface ChatMessageRepositoryCustom<T> {
    fun rem(key: Key<T>): Mono<Void>
    fun add(msg: Message<T, String>): Mono<Void>
}

@Suppress("unused")
class ChatMessageRepositoryCustomImpl<T>(val cassandra: ReactiveCassandraTemplate)
    : ChatMessageRepositoryCustom<T> {
    override fun rem(key: Key<T>): Mono<Void> =
            cassandra
                    .update(Query.query(where("msg_id").`is`(key.id)),
                            Update.empty().set("visible", false),
                            ChatMessageById::class.java
                    )
                    .then()

    override fun add(msg: Message<T, String>): Mono<Void> =
            cassandra
                    .insert(msg)
                    .then()
}