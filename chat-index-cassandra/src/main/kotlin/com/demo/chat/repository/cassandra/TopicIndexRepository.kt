package com.demo.chat.repository.cassandra

import com.demo.chat.domain.cassandra.ChatTopicName
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono


interface TopicByNameRepository<T> : ReactiveCassandraRepository<ChatTopicName<T>, T> {
    fun findByKeyName(name: String): Mono<ChatTopicName<T>>
}