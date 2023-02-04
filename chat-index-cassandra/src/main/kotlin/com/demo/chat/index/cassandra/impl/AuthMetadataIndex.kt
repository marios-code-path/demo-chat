package com.demo.chat.index.cassandra.impl

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

class AuthMetadataIndex<T>(
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
            PRINCIPAL -> principalRepository.findByPrincipalId(typeUtil.fromString(query[queryBy] ?: error("missing principal")))
            TARGET -> targetRepository.findByTargetId(typeUtil.fromString(query[queryBy] ?: error("missing target")))
            else -> Flux.error(Exception("Cannot find by query"))
        }.map { it.key }

    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> {
        TODO("Not yet implemented")
    }
}