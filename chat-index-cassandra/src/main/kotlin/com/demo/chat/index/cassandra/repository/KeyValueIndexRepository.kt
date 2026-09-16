package com.demo.chat.index.cassandra.repository

import com.demo.chat.index.cassandra.domain.ChatKeyValueIndex
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndexById
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux

interface KeyValueIndexRepository<T> : ReactiveCassandraRepository<ChatKeyValueIndex<T>, T> {
    fun findByKeyFieldAndKeyValue(field: String, value: String): Flux<ChatKeyValueIndex<T>>
}

interface KeyValueIndexByIdRepository<T> : ReactiveCassandraRepository<ChatKeyValueIndexById<T>, T> {
    fun findByKeyId(id: T): Flux<ChatKeyValueIndexById<T>>
}
