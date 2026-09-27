package com.demo.chat.controller.core.mapping

import com.demo.chat.controller.resolve.Verified
import com.demo.chat.domain.Key
import com.demo.chat.service.core.VerifiedKey
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
    /** The key verifies in its stored domain before the registry removes it. D1. */
    @MessageMapping("rem")
    fun remRoute(@Verified(anyDomain = true) key: VerifiedKey<T>): Mono<Void> = rem(key.key)

    // The two reads below ask the registry itself, so no verification runs first.
    @MessageMapping("exists")
    override fun exists(key: Key<T>): Mono<Boolean>
    @MessageMapping("rootOf")
    override fun rootOf(id: T): Mono<T & Any>
}
