package com.demo.chat.persistence.cassandra.repository

import com.demo.chat.persistence.cassandra.domain.CSKeyValuePair
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface KeyValuePairRepository<T> : ReactiveCassandraRepository<CSKeyValuePair<T>, T> {
    fun findByKeyId(id: T): Mono<CSKeyValuePair<T>>
    fun deleteByKeyId(id: T): Mono<Void>
    fun findByKeyIdIn(ids: List<T>): Flux<CSKeyValuePair<T>>
}