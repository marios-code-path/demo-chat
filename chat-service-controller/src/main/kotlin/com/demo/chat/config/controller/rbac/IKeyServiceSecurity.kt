package com.demo.chat.config.controller.rbac

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Mono

interface IKeyServiceSecurity<T> : IKeyService<T> {
    @PreAuthorize("hasRole('KEY')")
    override fun <S> key(kind: Class<S>): Mono<out Key<T>>

    @PreAuthorize("hasRole('KEY')")
    override fun exists(key: Key<T>): Mono<Boolean>

    @PreAuthorize("hasRole('KEY')")
    override fun rem(key: Key<T>): Mono<Void>
}