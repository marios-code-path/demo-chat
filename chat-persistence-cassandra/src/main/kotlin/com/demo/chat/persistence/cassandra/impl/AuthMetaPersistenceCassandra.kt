package com.demo.chat.persistence.cassandra.impl

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.persistence.cassandra.domain.AuthMetadataById
import com.demo.chat.persistence.cassandra.domain.AuthMetadataIdKey
import com.demo.chat.persistence.cassandra.repository.AuthMetadataRepository
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.AuthMetaPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/*
TODO: FIX For Single element deletions.
 */
/**
 * A grant stores the root of its principal and of its target, so a read
 * rebuilds both keys. The grant key takes the root of AUTH_METADATA. See
 * `CHAT-avduuqwp`.
 */
open class AuthMetaPersistenceCassandra<T : Any>(
    private val keyService: IKeyService<T>,
    private val rootKeys: RootKeys<T>,
    private val authMetadataRepo: AuthMetadataRepository<T>
) : AuthMetaPersistence<T> {
    override fun key(): Mono<out Key<T>> = keyService.key(ChatDomain.AUTH_METADATA)

    override fun add(ent: AuthMetadata<T>): Mono<Void> = authMetadataRepo.save(
        AuthMetadataById(
            AuthMetadataIdKey(ent.key.id),
            ent.target.id,
            ent.principal.id,
            ent.target.root,
            ent.principal.root,
            ent.permission,
            ent.mute,
            ent.expires
        )
    ).then()

    override fun rem(key: Key<T>): Mono<Void> =
        authMetadataRepo
            .findByKeyId(key.id)
            .switchIfEmpty(Mono.error(ChatException("Unknown AuthMetadata Key")))
            .flatMap { authMetadataRepo.delete(it) }

    override fun get(key: Key<T>): Mono<out AuthMetadata<T>> =
        authMetadataRepo
            .findByKeyId(key.id)
            .map(::grant)

    override fun all(): Flux<out AuthMetadata<T>> = authMetadataRepo.findAll().map(::grant)

    private fun grant(row: AuthMetadataById<T>): AuthMetadata<T> = AuthMetadata.create(
        Key.of(row.key.id, rootKeys.of(ChatDomain.AUTH_METADATA).id),
        Key.of(row.principalId, row.principalRoot),
        Key.of(row.targetId, row.targetRoot),
        row.permission,
        row.mute,
        row.expires,
    )
}
