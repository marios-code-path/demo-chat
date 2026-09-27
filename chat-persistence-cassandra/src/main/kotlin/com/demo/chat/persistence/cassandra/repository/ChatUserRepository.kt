package com.demo.chat.persistence.cassandra.repository

import com.demo.chat.domain.Key
import com.demo.chat.persistence.cassandra.domain.ChatUser
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatUserRepository<T : Any> : ReactiveCassandraRepository<ChatUser<T>, T>,
    ChatUserRepositoryCustom<T> {
    fun findByKeyId(id: T): Mono<ChatUser<T>>
    fun findByKeyIdIn(ids: List<T>): Flux<ChatUser<T>>
    fun deleteByKeyId(id: T): Mono<Void>
}

interface ChatUserRepositoryCustom<T> {
    fun add(u: ChatUser<T>): Mono<Void>
    fun rem(key: Key<T>): Mono<Void>
}

class ChatUserRepositoryCustomImpl<T>(val cassandra: ReactiveCassandraTemplate) : ChatUserRepositoryCustom<T> {
    override fun rem(key: Key<T>): Mono<Void> =
        cassandra
            .update(
                Query.query(where("user_id").`is`(key.id)),
                Update.empty().set("active", false),
                ChatUser::class.java
            )
            .then()

    override fun add(u: ChatUser<T>): Mono<Void> =
        cassandra
            .insert(u)
            .then()
}
