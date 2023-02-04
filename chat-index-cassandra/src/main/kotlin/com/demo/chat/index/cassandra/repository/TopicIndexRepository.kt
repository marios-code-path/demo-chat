package com.demo.chat.index.cassandra.repository

import com.demo.chat.index.cassandra.domain.ChatTopicName
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono


interface TopicByNameRepository<T> : ReactiveCassandraRepository<ChatTopicName<T>, T> {
    fun findByKeyName(name: String): Mono<ChatTopicName<T>>
}