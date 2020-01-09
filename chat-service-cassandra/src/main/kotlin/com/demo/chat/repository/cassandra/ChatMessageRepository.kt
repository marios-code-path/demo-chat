package com.demo.chat.repository.cassandra

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.cassandra.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatMessageByUserRepository<T> : ReactiveCassandraRepository<ChatMessageByUser<T>, ChatMessageByUserKey<T>> {
    fun findByKeyFrom(userId: T): Flux<ChatMessageByUser<T>>
}

interface ChatMessageByTopicRepository<T> : ReactiveCassandraRepository<ChatMessageByTopic<T>, ChatMessageByTopicKey<T>> {
    fun findByKeyDest(topicId: T): Flux<ChatMessageByTopic<T>>
}

interface ChatMessageRepository<T> : ChatMessageRepositoryCustom<T>, ReactiveCassandraRepository<ChatMessageById<T>, ChatMessageByIdKey<T>> {
    fun findByKeyId(id: T): Mono<ChatMessageById<T>>
}

interface ChatMessageRepositoryCustom<T> {
    fun rem(key: Key<T>): Mono<Void>
    fun add(msg: Message<T, String>): Mono<Void>
}

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