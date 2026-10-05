package com.demo.chat.index.cassandra.repository

import com.demo.chat.index.cassandra.domain.ChatMessageByTopic
import com.demo.chat.index.cassandra.domain.ChatMessageByUser
import com.demo.chat.index.cassandra.domain.ChatMessageIndexById
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * A removal by message id alone is not possible here, because the partition
 * is the sender. `MessageIndex.rem` reads [ChatMessageIndexByIdRepository]
 * for the full key. See `CHAT-xcmpudyb`.
 */
interface ChatMessageByUserRepository<T : Any> :
    ChatMessageByUserRepositoryCustom<T>, ReactiveCassandraRepository<ChatMessageByUser<T>, T> {
    fun findByKeyFrom(userId: T): Flux<ChatMessageByUser<T>>
}

/** The partition is the room. The removal rule of the sender table applies. */
interface ChatMessageByTopicRepository<T : Any> :
    ChatMessageByTopicRepositoryCustom<T>, ReactiveCassandraRepository<ChatMessageByTopic<T>, T> {
    fun findByKeyDest(topicId: T): Flux<ChatMessageByTopic<T>>
}

interface ChatMessageIndexByIdRepository<T : Any> : ReactiveCassandraRepository<ChatMessageIndexById<T>, T>

/**
 * A delete by entity names every field of the key class, and the key class
 * also holds a regular column. Cassandra refuses that query. So each removal
 * names the three primary key columns alone. See `CHAT-xcmpudyb`.
 */
interface ChatMessageByUserRepositoryCustom<T> {
    fun rem(row: ChatMessageIndexById<T>): Mono<Void>
}

/** The room equivalent of [ChatMessageByUserRepositoryCustom]. */
interface ChatMessageByTopicRepositoryCustom<T> {
    fun rem(row: ChatMessageIndexById<T>): Mono<Void>
}

@Suppress("unused")
class ChatMessageByUserRepositoryCustomImpl<T>(val cassandra: ReactiveCassandraTemplate) :
    ChatMessageByUserRepositoryCustom<T> {
    override fun rem(row: ChatMessageIndexById<T>): Mono<Void> = cassandra
        .delete(
            Query.query(where("user_id").`is`(row.from), where("msg_time").`is`(row.timestamp), where("msg_id").`is`(row.id)),
            ChatMessageByUser::class.java
        )
        .then()
}

@Suppress("unused")
class ChatMessageByTopicRepositoryCustomImpl<T>(val cassandra: ReactiveCassandraTemplate) :
    ChatMessageByTopicRepositoryCustom<T> {
    override fun rem(row: ChatMessageIndexById<T>): Mono<Void> = cassandra
        .delete(
            Query.query(where("topic_id").`is`(row.dest), where("msg_time").`is`(row.timestamp), where("msg_id").`is`(row.id)),
            ChatMessageByTopic::class.java
        )
        .then()
}
