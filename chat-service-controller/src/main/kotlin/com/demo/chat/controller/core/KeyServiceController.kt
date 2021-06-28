package com.demo.chat.controller.core

import com.demo.chat.domain.Key
import com.demo.chat.controller.core.mapping.IKeyServiceMapping
import com.demo.chat.service.IKeyService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Mono

open class KeyServiceController<T>(private val that: IKeyService<T>): IKeyServiceMapping<T> {
    @MessageMapping("key")
    override fun <S> key(kind: Class<S>): Mono<out Key<T>> = that.key(kind)

    @MessageMapping("rem")
    override fun rem(key: Key<T>): Mono<Void> = that.rem(key)

    @MessageMapping("exists")
    override fun exists(key: Key<T>): Mono<Boolean> = that.exists(key)
}