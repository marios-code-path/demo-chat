package com.demo.chat.service.persistence

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.repository.cassandra.AuthMetadataRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.service.security.AuthMetaPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/*
TODO: FIX For Single element deletions.
 */
open class AuthMetaPersistenceCassandra<T>(
    private val keyService: IKeyService<T>,
    private val authMetadataRepo: AuthMetadataRepository<T>
) : AuthMetaPersistence<T> {
    override fun key(): Mono<out Key<T>> = keyService.key(AuthMetadata::class.java)

    override fun add(ent: AuthMetadata<T>): Mono<Void> = authMetadataRepo.save(ent).then()

    override fun rem(key: Key<T>): Mono<Void> =
        authMetadataRepo
            .findByKeyId(key.id)
            .switchIfEmpty(Mono.error(ChatException("Unknown AuthMetadata Key")))
            .flatMap { authMetadataRepo.delete(it) }

    override fun get(key: Key<T>): Mono<out AuthMetadata<T>> =
        authMetadataRepo
            .findByKeyId(key.id)

    override fun all(): Flux<out AuthMetadata<T>> = authMetadataRepo.findAll()
}