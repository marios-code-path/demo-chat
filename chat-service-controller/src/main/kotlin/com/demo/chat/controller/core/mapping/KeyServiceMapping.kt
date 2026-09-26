package com.demo.chat.controller.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.IKeyService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Mono

/**
 * The key routes. A mint names a domain from the closed list, and Jackson
 * refuses any other value. See `CHAT-avduuqwp`, D3 of section D.
 */
interface IKeyServiceMapping<T> : IKeyService<T> {
    @MessageMapping("key")
    override fun key(domain: ChatDomain): Mono<out Key<T>>
    @MessageMapping("rem")
    override fun rem(key: Key<T>): Mono<Void>
    @MessageMapping("exists")
    override fun exists(key: Key<T>): Mono<Boolean>
    @MessageMapping("rootOf")
    override fun rootOf(id: T): Mono<T & Any>
}
