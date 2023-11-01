package com.demo.chat.controller.core.access

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Mono

interface IKeyServiceAccess<T> : IKeyService<T> {

    @PreAuthorize("@chatAccess.hasAccessToDomain(#kind, 'NEW')")
    override fun <S> key(kind: Class<S>): Mono<out Key<T>>

    @PreAuthorize("@chatAccess.hasAccessTo(#key, 'DEL')")
    override fun rem(key: Key<T>): Mono<Void>
    override fun exists(key: Key<T>): Mono<Boolean>
}