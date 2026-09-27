package com.demo.chat.index.cassandra.impl

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipal
import com.demo.chat.index.cassandra.domain.AuthMetadataByTarget
import com.demo.chat.index.cassandra.repository.AuthMetadataByPrincipalRepository
import com.demo.chat.index.cassandra.repository.AuthMetadataByTargetRepository
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaIndex.Companion.PRINCIPAL
import com.demo.chat.service.security.AuthMetaIndex.Companion.TARGET
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AuthMetadataIndex<T : Any>(
    private val typeUtil: TypeUtil<T>,
    private val targetRepository: AuthMetadataByTargetRepository<T>,
    private val principalRepository: AuthMetadataByPrincipalRepository<T>,
    private val rootKeys: RootKeys<T>,
) : AuthMetaIndex<T, Map<String, String>> {
    override fun add(entity: AuthMetadata<T>): Mono<Void> {
        val saved = targetRepository.save(
            AuthMetadataByTarget(
                entity.key.id,
                entity.target.id,
                entity.principal.id,
                entity.target.root,
                entity.principal.root,
                entity.permission,
                entity.mute,
                entity.expires
            )
        )
            .then(
                principalRepository.save(
                    AuthMetadataByPrincipal(
                        entity.key.id,
                        entity.target.id,
                        entity.principal.id,
                        entity.target.root,
                        entity.principal.root,
                        entity.permission,
                        entity.mute,
                        entity.expires
                    )
                )
            )
        return saved.then()
    }

    override fun rem(key: Key<T>): Mono<Void> {
        val keyId = requireNotNull(key.id)
        val removed = targetRepository.deleteById(keyId)
            .then(principalRepository.deleteById(keyId))

        return removed.then()
    }

    override fun findBy(query: Map<String, String>): Flux<out Key<T>> =
        when (val queryBy = query.keys.first()) {
            PRINCIPAL -> principalRepository.findByPrincipalId(typeUtil.fromString(query[queryBy] ?: error("missing principal")))
                .map { grantKey(it.keyId) }
            TARGET -> targetRepository.findByTargetId(typeUtil.fromString(query[queryBy] ?: error("missing target")))
                .map { grantKey(it.keyId) }
            else -> Flux.error(Exception("Cannot find by query"))
        }

    /** A grant key carries the root of the AUTH_METADATA domain. See `CHAT-avduuqwp`. */
    private fun grantKey(id: T): Key<T> = Key.of(id, rootKeys.of(ChatDomain.AUTH_METADATA).id)

    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> {
        TODO("Not yet implemented")
    }
}
