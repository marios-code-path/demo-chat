package com.demo.chat.index.cassandra.impl

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipal
import com.demo.chat.index.cassandra.domain.AuthMetadataByPrincipalKey
import com.demo.chat.index.cassandra.domain.AuthMetadataByTarget
import com.demo.chat.index.cassandra.domain.AuthMetadataByTargetKey
import com.demo.chat.index.cassandra.domain.AuthMetadataById
import com.demo.chat.index.cassandra.repository.AuthMetadataByIdRepository
import com.demo.chat.index.cassandra.repository.AuthMetadataByPrincipalRepository
import com.demo.chat.index.cassandra.repository.AuthMetadataByTargetRepository
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaIndex.Companion.PRINCIPAL
import com.demo.chat.service.security.AuthMetaIndex.Companion.TARGET
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The grant index of the Cassandra backend.
 *
 * A grant writes three rows. Two rows index it by target and by principal.
 * The third row carries the target and the principal under the grant id, so
 * a removal can name the partitions it must clear.
 *
 * A target holds many grants, because the grant id is a clustering column.
 * See `CHAT-rmxxtwtu`.
 */
class AuthMetadataIndex<T : Any>(
    private val typeUtil: TypeUtil<T>,
    private val targetRepository: AuthMetadataByTargetRepository<T>,
    private val principalRepository: AuthMetadataByPrincipalRepository<T>,
    private val byIdRepository: AuthMetadataByIdRepository<T>,
    private val rootKeys: RootKeys<T>,
) : AuthMetaIndex<T, Map<String, String>> {
    override fun add(entity: AuthMetadata<T>): Mono<Void> {
        val byTarget = AuthMetadataByTarget(
            AuthMetadataByTargetKey(entity.target.id, entity.key.id),
            entity.principal.id,
            entity.target.root,
            entity.principal.root,
            entity.permission,
            entity.mute,
            entity.expires
        )
        val byPrincipal = AuthMetadataByPrincipal(
            AuthMetadataByPrincipalKey(entity.principal.id, entity.key.id),
            entity.target.id,
            entity.target.root,
            entity.principal.root,
            entity.permission,
            entity.mute,
            entity.expires
        )
        val byId = AuthMetadataById(
            entity.key.id,
            entity.target.id,
            entity.principal.id,
            entity.target.root,
            entity.principal.root
        )

        return targetRepository.save(byTarget)
            .then(principalRepository.save(byPrincipal))
            .then(byIdRepository.save(byId))
            .then()
    }

    /**
     * Removes every row of the supplied grant.
     *
     * The primary key of the two index tables is the target or the principal,
     * so a removal by the grant id alone clears the wrong row. The by-id row
     * names the two partitions that hold the rows of this grant.
     *
     * The removals of one grant run in order. A failure stops the chain and
     * reports, so a partial removal is visible rather than silent.
     */
    override fun rem(key: Key<T>): Mono<Void> {
        val keyId = requireNotNull(key.id)

        return byIdRepository
            .findByKeyId(keyId)
            .concatMap { row ->
                targetRepository
                    .deleteById(AuthMetadataByTargetKey(row.targetId, keyId))
                    .then(principalRepository.deleteById(AuthMetadataByPrincipalKey(row.principalId, keyId)))
                    .then(byIdRepository.deleteById(keyId))
            }
            .then()
    }

    override fun findBy(query: Map<String, String>): Flux<out Key<T>> =
        when (val queryBy = query.keys.first()) {
            PRINCIPAL -> principalRepository.findByKeyPrincipalId(typeUtil.fromString(query[queryBy] ?: error("missing principal")))
                .map { grantKey(it.key.id) }
            TARGET -> targetRepository.findByKeyTargetId(typeUtil.fromString(query[queryBy] ?: error("missing target")))
                .map { grantKey(it.key.id) }
            else -> Flux.error(Exception("Cannot find by query"))
        }

    /** A grant key carries the root of the AUTH_METADATA domain. See `CHAT-avduuqwp`. */
    private fun grantKey(id: T): Key<T> = Key.of(id, rootKeys.of(ChatDomain.AUTH_METADATA).id)

    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> {
        TODO("Not yet implemented")
    }
}
