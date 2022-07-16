package com.demo.chat.repository.cassandra

import com.demo.chat.domain.cassandra.AuthMetadataByPrincipal
import com.demo.chat.domain.cassandra.AuthMetadataByTarget
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux

interface AuthMetadataByPrincipalRepository<T> : ReactiveCassandraRepository<AuthMetadataByPrincipal<T>, T> {
    fun findByPrincipalId(id: T): Flux<AuthMetadataByPrincipal<T>>
}

interface AuthMetadataByTargetRepository<T> : ReactiveCassandraRepository<AuthMetadataByTarget<T>, T> {
    fun findByTargetId(id: T): Flux<AuthMetadataByTarget<T>>
}
