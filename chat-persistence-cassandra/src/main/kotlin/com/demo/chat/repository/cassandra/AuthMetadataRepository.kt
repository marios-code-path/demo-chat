package com.demo.chat.repository.cassandra

import com.demo.chat.domain.cassandra.AuthMetadataById
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono

interface AuthMetadataRepository<T> : ReactiveCassandraRepository<AuthMetadataById<T>, T> {
    fun findByKeyId(id: T) : Mono<AuthMetadataById<T>>
}