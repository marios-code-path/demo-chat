package com.demo.chat.controller.core

import com.demo.chat.controller.resolve.keyDomainOf

import com.demo.chat.controller.resolve.Verified

import com.demo.chat.service.core.VerifiedKey

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IndexService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class IndexSearchRequestIndexServiceController<T, E>(
    private val that: IndexService<T, E, IndexSearchRequest>,
    private val verifier: KeyVerifier<T>,
) : IndexService<T, E, IndexSearchRequest> by that {
    /** The entity key verifies in the index domain before the index write. See `CHAT-avduuqwp`, D12. */
    @MessageMapping("add")
    fun addRoute(entity: E): Mono<Void> =
        verifier.verifyEntity(entity, keyDomainOf(this::class.java))
            .then(verifier.verifyReferences(entity))
            .then(Mono.defer { that.add(entity) })

    @MessageMapping("rem")
    fun remRoute(@Verified key: VerifiedKey<T>): Mono<Void> = that.rem(key.key)

    @MessageMapping("query")
    override fun findBy(query: IndexSearchRequest): Flux<out Key<T>> = that.findBy(query)

    @MessageMapping("unique")
    override fun findUnique(query: IndexSearchRequest): Mono<out Key<T>> = that.findUnique(query)
}

open class MapIndexServiceController<T, E>(
    private val that: IndexService<T, E, Map<String, String>>,
    private val verifier: KeyVerifier<T>,
) : IndexService<T, E, Map<String, String>> by that {
    /** The entity key verifies in the index domain before the index write. See `CHAT-avduuqwp`, D12. */
    @MessageMapping("add")
    fun addRoute(entity: E): Mono<Void> =
        verifier.verifyEntity(entity, keyDomainOf(this::class.java))
            .then(verifier.verifyReferences(entity))
            .then(Mono.defer { that.add(entity) })

    @MessageMapping("rem")
    fun remRoute(@Verified key: VerifiedKey<T>): Mono<Void> = that.rem(key.key)

    @MessageMapping("query")
    override fun findBy(query: Map<String, String>): Flux<out Key<T>> = that.findBy(query)

    @MessageMapping("unique")
    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> = that.findUnique(query)
}
