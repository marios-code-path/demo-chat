package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.*
import com.demo.chat.service.core.PersistenceStore
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PersistenceRestMapping<T, E> : PersistenceStore<T, E> {
    @PostMapping("/key",  produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    override fun key(): Mono<out Key<T>>

    @DeleteMapping("/rem/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun restRem(@PathVariable id: T): Mono<Void> = rem(Key.funKey(id))

    @GetMapping("/get/{id}",  produces = [MediaType.APPLICATION_JSON_VALUE])
    fun restGet(@PathVariable id: T): Mono<out E> = get(Key.funKey(id))

    @GetMapping("/all", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun all(): Flux<out E>
}

