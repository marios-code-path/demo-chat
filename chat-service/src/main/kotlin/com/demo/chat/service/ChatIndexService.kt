package com.demo.chat.service

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Given Key [K], and Query[Q] we will add, remove and seek K's for a given Q
 */
interface ChatIndexService<K, Q> {
    fun add(key: K, criteria: Q): Mono<Void>
    fun rem(key: K): Mono<Void>
    fun findBy(query: Q): Flux<K>
}