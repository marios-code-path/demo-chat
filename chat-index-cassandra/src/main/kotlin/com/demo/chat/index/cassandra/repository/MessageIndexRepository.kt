package com.demo.chat.index.cassandra.repository

import com.demo.chat.index.cassandra.domain.ChatMessageByTopic
import com.demo.chat.index.cassandra.domain.ChatMessageByUser
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatMessageByUserRepository<T> : ReactiveCassandraRepository<ChatMessageByUser<T>, T> {
    fun findByKeyFrom(userId: T): Flux<ChatMessageByUser<T>>
    fun deleteByKeyId(msgId: T): Mono<Void>
}

interface ChatMessageByTopicRepository<T> : ReactiveCassandraRepository<ChatMessageByTopic<T>, T> {
    fun findByKeyDest(topicId: T): Flux<ChatMessageByTopic<T>>
    fun deleteByKeyId(msgId: T): Mono<Void>
}