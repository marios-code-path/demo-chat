package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.service.core.IndexService
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface IndexRestMapping<T, E, Q> : IndexService<T, E, Q> {
    @PutMapping("/add")
    override fun add(entity: E): Mono<Void>

    @DeleteMapping("/rem")
    override fun rem(key: Key<T>): Mono<Void>

    @GetMapping("/findBy")
    override fun findBy(query: Q): Flux<out Key<T>>

    @GetMapping("/findUnique")
    override fun findUnique(query: Q): Mono<out Key<T>>
}