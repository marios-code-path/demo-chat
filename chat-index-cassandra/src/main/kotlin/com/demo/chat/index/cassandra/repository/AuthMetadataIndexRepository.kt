package com.demo.chat.index.cassandra.repository

import com.demo.chat.index.cassandra.domain.AuthMetadataById
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipal
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipalKey
import com.demo.chat.index.cassandra.domain.AuthMetadataByTarget
import com.demo.chat.index.cassandra.domain.AuthMetadataByTargetKey
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository
import reactor.core.publisher.Flux

interface AuthMetadataByPrincipalRepository<T : Any> : ReactiveCassandraRepository<
        AuthMetadataByPrincipal<T>, AuthMetadataByPrincipalKey<T>> {
    fun findByKeyPrincipalId(id: T): Flux<AuthMetadataByPrincipal<T>>
}

interface AuthMetadataByTargetRepository<T : Any> : ReactiveCassandraRepository<
        AuthMetadataByTarget<T>, AuthMetadataByTargetKey<T>> {
    fun findByKeyTargetId(id: T): Flux<AuthMetadataByTarget<T>>
}

interface AuthMetadataByIdRepository<T : Any> : ReactiveCassandraRepository<AuthMetadataById<T>, T> {
    fun findByKeyId(id: T): Flux<AuthMetadataById<T>>
}
