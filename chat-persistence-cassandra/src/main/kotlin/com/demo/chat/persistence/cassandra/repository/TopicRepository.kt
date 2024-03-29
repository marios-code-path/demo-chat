package com.demo.chat.persistence.cassandra.repository

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.persistence.cassandra.domain.ChatTopic
import com.demo.chat.persistence.cassandra.domain.ChatTopicKey
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono

interface TopicRepository<T> :
        ReactiveCassandraRepository<ChatTopic<T>, T>,
    TopicRepositoryCustom<T> {
    fun findByKeyId(id: T): Mono<ChatTopic<T>>
}

interface TopicRepositoryCustom<T> {
    fun add(messageTopic: MessageTopic<T>): Mono<Void>
    fun rem(roomKey: Key<T>): Mono<Void>
}

@Suppress("unused")
class TopicRepositoryCustomImpl<T>(val cassandra: ReactiveCassandraTemplate) :
    TopicRepositoryCustom<T> {
    override fun rem(roomKey: Key<T>): Mono<Void> =
            cassandra
                    .update(Query.query(where("room_id").`is`(roomKey.id)),
                            Update.empty().set("active", false),
                            ChatTopic::class.java
                    )
                    .then()

    override fun add(messageTopic: MessageTopic<T>): Mono<Void> = cassandra
            .insert(
                ChatTopic(
                    ChatTopicKey(
                            messageTopic.key.id
                    ),
                    messageTopic.data,
                    true)
            )
            .then()
}