package com.demo.chat.persistence.cassandra.repository

import com.demo.chat.persistence.cassandra.domain.KeyCredentialById
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Mono

interface KeyCredentialRepository<T : Any>: ReactiveCassandraRepository<KeyCredentialById<T>, T> {
    fun findByKeyId(id: T): Mono<KeyCredentialById<T>>
}