package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.TypeUtil
import com.demo.chat.controller.webflux.resolve.DomainScoped
import com.demo.chat.controller.webflux.resolve.Resolved
import com.demo.chat.service.core.VerifiedKey

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.domain.*
import com.demo.chat.service.core.IndexService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface IndexRestMapping<T, E, Q> : IndexService<T, E, Q>, DomainScoped {
    /**
     * The entity is caller input, so its key verifies in the index domain
     * before the index write. A forged key never reaches the index. See
     * `CHAT-avduuqwp`, D12.
     */
    @PutMapping(
        "/add", consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.CREATED)
    fun restAdd(@RequestBody entity: E): Mono<Void> =
        // defer keeps the store call out of assembly, so a refused key never calls it.
        verifier().verifyEntity(entity, domain())
            .then(verifier().verifyReferences(entity))
            .then(Mono.defer { add(entity) })

    @DeleteMapping("/rem/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restRem(@Resolved id: VerifiedKey<T>): Mono<Void> = rem(id.key)

    fun typeUtil(): TypeUtil<T>

    fun verifier(): KeyVerifier<T>

    /** The domain of this index. A path id resolves in it. See `CHAT-avduuqwp`, C69. */
    override fun domain(): ChatDomain

    @GetMapping(
        "/findBy", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun findBy(@ModelAttribute query: Q): Flux<out Key<T>>

    @GetMapping(
        "/findUnique", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun findUnique(@ModelAttribute query: Q): Mono<out Key<T>>
}