package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.IKeyService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono


interface IKeyRestMapping<T> : IKeyService<T> {

    // T is erased where Spring resolves the path segment, so the segment arrives
    // as the String it is on the wire and typeUtil() converts it to the key type.
    fun typeUtil(): TypeUtil<T>

    fun verifier(): KeyVerifier<T>

    @PostMapping("/new",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun restKey(@RequestBody req: DomainRequest): Mono<out Key<T>> = key(req.domain)

    @DeleteMapping("/rem/{id}",
        produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restRem(@PathVariable id: String): Mono<Void> =
        verifier().resolve(typeUtil().fromString(id), null).flatMap { rem(it.key) }

    @GetMapping("/exists/{id}",
        produces = [MediaType.APPLICATION_JSON_VALUE])
    fun restExists(@PathVariable id: String): Mono<Boolean> =
        verifier().resolve(typeUtil().fromString(id), null).flatMap { exists(it.key) }.onErrorReturn(false)

}

/**
 * A mint request names a domain from the closed list. Jackson refuses any
 * other value with 400, so no class name reaches a class loader. See
 * `CHAT-avduuqwp`, D3 of section D.
 */
data class DomainRequest(val domain: ChatDomain)