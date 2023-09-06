package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.*
import com.demo.chat.service.core.PersistenceStore
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PersistenceRestMapping<T, E> : PersistenceStore<T, E> {
    @PostMapping("/key")
    override fun key(): Mono<out Key<T>>

    @PutMapping("/add")
    override fun add(ent: E): Mono<Void>

    @DeleteMapping("/rem")
    override fun rem(key: Key<T>): Mono<Void>

    @GetMapping("/get")
    override fun get(key: Key<T>): Mono<out E>

    @GetMapping("/all")
    override fun all(): Flux<out E>
}

