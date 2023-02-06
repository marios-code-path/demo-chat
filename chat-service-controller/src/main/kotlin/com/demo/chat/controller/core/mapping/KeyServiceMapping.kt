package com.demo.chat.controller.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Mono

interface  IKeyServiceMapping <T> : IKeyService<T> {
    @MessageMapping("key")
    override fun <S> key(kind: Class<S>): Mono<out Key<T>>
    @MessageMapping("rem")
    override fun rem(key: Key<T>): Mono<Void>
    @MessageMapping("exists")
    override fun exists(key: Key<T>): Mono<Boolean>
}