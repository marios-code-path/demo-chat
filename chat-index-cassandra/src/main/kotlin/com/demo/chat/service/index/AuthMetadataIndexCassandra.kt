package com.demo.chat.service.index

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.cassandra.AuthMetadataByPrincipal
import com.demo.chat.domain.cassandra.AuthMetadataByTarget
import com.demo.chat.repository.cassandra.AuthMetadataByPrincipalRepository
import com.demo.chat.repository.cassandra.AuthMetadataByTargetRepository
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaIndex.Companion.PRINCIPAL
import com.demo.chat.service.security.AuthMetaIndex.Companion.TARGET
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class AuthMetadataIndexCassandra<T>(
    private val typeUtil: TypeUtil<T>,
    private val targetRepository: AuthMetadataByTargetRepository<T>,
    private val principalRepository: AuthMetadataByPrincipalRepository<T>,
) : AuthMetaIndex<T, Map<String, String>> {
    override fun add(entity: AuthMetadata<T>): Mono<Void> {
        val saved = targetRepository.save(
            AuthMetadataByTarget(
                entity.key.id,
                entity.target.id,
                entity.principal.id,
                entity.permission,
                entity.expires
            )
        )
            .then(
                principalRepository.save(
                    AuthMetadataByPrincipal(
                        entity.key.id,
                        entity.target.id,
                        entity.principal.id,
                        entity.permission,
                        entity.expires
                    )
                )
            )
        return saved.then()
    }

    override fun rem(key: Key<T>): Mono<Void> {
        val removed = targetRepository.deleteById(key.id)
            .then(principalRepository.deleteById(key.id))

        return removed.then()
    }

    override fun findBy(query: Map<String, String>): Flux<out Key<T>> =
        when (val queryBy = query.keys.first()) {
            PRINCIPAL -> principalRepository.findByPrincipalId(typeUtil.fromString(queryBy ?: error("missing principal")))
            TARGET -> targetRepository.findByTargetId(typeUtil.fromString(queryBy ?: error("missing target")))
            else -> Flux.empty()
        }.map { it.key }

    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> {
        TODO("Not yet implemented")
    }
}