package com.demo.chat.repository.cassandra

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.cassandra.ChatTopic
import com.demo.chat.domain.cassandra.ChatTopicName
import com.demo.chat.domain.cassandra.ChatTopicKey
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono


interface TopicByNameRepository<T> : ReactiveCassandraRepository<ChatTopicName<T>, T> {
    fun findByKeyName(name: String): Mono<out ChatTopicName<T>>
}

interface TopicRepository<T> :
        ReactiveCassandraRepository<ChatTopic<T>, T>,
        TopicRepositoryCustom<T> {
    fun findByKeyId(id: T): Mono<out MessageTopic<T>>
}

interface TopicRepositoryCustom<T> {
    fun add(messageTopic: MessageTopic<T>): Mono<Void>
    fun rem(roomKey: Key<T>): Mono<Void>
}

class TopicRepositoryCustomImpl<T>(val cassandra: ReactiveCassandraTemplate) :
        TopicRepositoryCustom<T> {
    override fun rem(key: Key<T>): Mono<Void> =
            cassandra
                    .update(Query.query(where("room_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatTopic::class.java
                    )
                    .then()

    override fun add(messageTopic: MessageTopic<T>): Mono<Void> = cassandra
            .insert(ChatTopic(
                    ChatTopicKey(
                            messageTopic.key.id
                    ),
                    messageTopic.data,
                    true))
            .then()
}