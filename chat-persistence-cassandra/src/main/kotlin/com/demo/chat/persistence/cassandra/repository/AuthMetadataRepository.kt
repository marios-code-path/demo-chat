package com.demo.chat.persistence.cassandra.repository

import com.demo.chat.persistence.cassandra.domain.AuthMetadataById
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono

interface AuthMetadataRepository<T> : ReactiveCassandraRepository<AuthMetadataById<T>, T> {
    fun findByKeyId(id: T) : Mono<AuthMetadataById<T>>
}