package com.demo.chat.controller.webflux.core.mapping

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
    fun restRem(@PathVariable id: T): Mono<Void> = rem(Key.funKey(id))

    @GetMapping(
        "/findBy", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun findBy(@ModelAttribute query: Q): Flux<out Key<T>>

    @GetMapping(
        "/findUnique", produces = [MediaType.APPLICATION_JSON_VALUE])
    override fun findUnique(@ModelAttribute query: Q): Mono<out Key<T>>
}