package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.TypeUtil

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.domain.*
import com.demo.chat.service.core.IndexService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface IndexRestMapping<T, E, Q> : IndexService<T, E, Q> {
    @PutMapping(
        "/add", consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.CREATED)
    override fun add(@RequestBody entity: E): Mono<Void>

    @DeleteMapping("/rem/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // T is erased where Spring resolves the path segment, so the segment arrives
    // as the String it is on the wire and typeUtil() converts it to the key type.
    fun restRem(@PathVariable id: String): Mono<Void> =
        verifier().resolve(typeUtil().fromString(id), domain()).flatMap { rem(it.key) }

    fun typeUtil(): TypeUtil<T>

    fun verifier(): KeyVerifier<T>

    /** The domain of this index. A path id resolves in it. See `CHAT-avduuqwp`, C69. */
    fun domain(): ChatDomain

    @GetMapping(
        "/findBy", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun findBy(@ModelAttribute query: Q): Flux<out Key<T>>

    @GetMapping(
        "/findUnique", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun findUnique(@ModelAttribute query: Q): Mono<out Key<T>>
}