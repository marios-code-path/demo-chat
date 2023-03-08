package com.demo.chat.index.cassandra.repository

import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipal
import com.demo.chat.index.cassandra.domain.AuthMetadataByTarget
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux

interface AuthMetadataByPrincipalRepository<T> : ReactiveCassandraRepository<
        AuthMetadataByPrincipal<T>, T> {
    fun findByPrincipalId(id: T): Flux<AuthMetadataByPrincipal<T>>
}

interface AuthMetadataByTargetRepository<T> : ReactiveCassandraRepository<AuthMetadataByTarget<T>, T> {
    fun findByTargetId(id: T): Flux<AuthMetadataByTarget<T>>
}
