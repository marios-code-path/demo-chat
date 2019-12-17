package com.demo.chat.repository.cassandra

import com.demo.chat.domain.Key
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.cassandra.ChatMessageById
import com.demo.chat.domain.cassandra.ChatMessageByTopic
import com.demo.chat.domain.cassandra.ChatMessageByUser
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatMessageByUserRepository<K> : ReactiveCassandraRepository<ChatMessageByUser<K>, K> {
    fun findByKeyUserId(userId: K): Flux<ChatMessageByUser<K>>
}

interface ChatMessageByTopicRepository<K> : ReactiveCassandraRepository<ChatMessageByTopic<K>, K> {
    fun findByKeyTopicId(topicId: K): Flux<ChatMessageByTopic<K>>
}

interface ChatMessageRepository<K> : ChatMessageRepositoryCustom<K>, ReactiveCassandraRepository<ChatMessageById<K>, K> {
    fun findByKeyId(id: K): Mono<ChatMessageById<K>>
}

interface ChatMessageRepositoryCustom<K> {
    fun rem(key: Key<K>): Mono<Void>
    fun add(msg: TextMessage<K>): Mono<Void>
}

class ChatMessageRepositoryCustomImpl<K>(val cassandra: ReactiveCassandraTemplate)
    : ChatMessageRepositoryCustom<K> {
    override fun rem(key: Key<K>): Mono<Void> =
            cassandra
                    .update(Query.query(where("msg_id").`is`(key.id)),
                            Update.empty().set("visible", false),
                            ChatMessageById::class.java
                    )
                    .then()

    override fun add(msg: TextMessage<K>): Mono<Void> =
            cassandra
                    .insert(msg)
                    .then()

}