package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.*
import com.demo.chat.service.core.PersistenceStore
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PersistenceRestMapping<T, E> : PersistenceStore<T, E> {
    // The controller is generic in T, so T is erased where Spring resolves an
    // argument: it cannot bind a path segment as the key type. The segment
    // arrives as the String it is on the wire, and typeUtil() converts it to the
    // key type this store actually uses.
    fun typeUtil(): TypeUtil<T>

    @PostMapping("/key",  produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    override fun key(): Mono<out Key<T>>

    @DeleteMapping("/rem/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restRem(@PathVariable id: String): Mono<Void> = rem(Key.funKey(typeUtil().fromString(id)))

    @GetMapping("/get/{id}",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun restGet(@PathVariable id: String): Mono<out E> = get(Key.funKey(typeUtil().fromString(id)))

    @GetMapping("/all", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun all(): Flux<out E>
}

