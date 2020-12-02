package com.demo.chat.service

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface LoadableService {
    fun load(): Mono<Void>
}