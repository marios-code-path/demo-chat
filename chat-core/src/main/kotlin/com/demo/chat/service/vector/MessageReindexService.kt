package com.demo.chat.service.vector

import reactor.core.publisher.Mono

interface MessageReindexService<T> {
    fun start(): Mono<VectorIndexStatus>

    fun status(): VectorIndexStatus
}
