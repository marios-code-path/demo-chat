package com.demo.chat.repository.cassandra

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.cassandra.ChatMessageTopic
import com.demo.chat.domain.cassandra.ChatMessageTopicName
import com.demo.chat.domain.cassandra.ChatTopicKey
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono


interface TopicByNameRepository<K> : ReactiveCassandraRepository<ChatMessageTopicName<K>, K> {
    fun findByKeyName(name: String): Mono<out Key<K>>
}

interface TopicRepository<K> :
        ReactiveCassandraRepository<ChatMessageTopic<K>, K>,
        TopicRepositoryCustom<K> {
    fun findByKeyId(id: K): Mono<out MessageTopic<K>>
}

interface TopicRepositoryCustom<K> {
    fun add(messageTopic: MessageTopic<K>): Mono<Void>
    fun rem(roomKey: Key<K>): Mono<Void>
}

class TopicRepositoryCustomImpl<K>(val cassandra: ReactiveCassandraTemplate) :
        TopicRepositoryCustom<K> {
    override fun rem(key: Key<K>): Mono<Void> =
            cassandra
                    .update(Query.query(where("room_id").`is`(key.id)),
                            Update.empty().set("active", false),
                            ChatMessageTopic::class.java
                    )
                    .then()

    override fun add(messageTopic: MessageTopic<K>): Mono<Void> = cassandra
            .insert(ChatMessageTopic(
                    ChatTopicKey(
                            messageTopic.key.id
                    ),
                    messageTopic.data,
                    true))
            .then()
}