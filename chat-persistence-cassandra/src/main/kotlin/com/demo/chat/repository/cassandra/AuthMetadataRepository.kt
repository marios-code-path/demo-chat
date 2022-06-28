package com.demo.chat.repository.cassandra

import com.demo.chat.domain.AuthMetadata
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AuthMetadataRepository<T> : ReactiveCassandraRepository<AuthMetadata<T>, T> {
    fun findByKeyId(id: T): Mono<AuthMetadata<T>>
    fun findByKeyIn(ids: List<T>): Flux<AuthMetadata<T>>
}