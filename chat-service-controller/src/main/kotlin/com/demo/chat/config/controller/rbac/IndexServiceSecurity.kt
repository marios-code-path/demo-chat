package com.demo.chat.config.controller.rbac

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IndexService
import org.springframework.security.access.prepost.PreAuthorize
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


interface IndexServiceSecurity<T, E, Q> : IndexService<T, E, Q> {

    @PreAuthorize("hasRole('WRITE')")
    override fun add(entity: E): Mono<Void>

    @PreAuthorize("hasRole('WRITE')")
    override fun rem(key: Key<T>): Mono<Void>

    @PreAuthorize("hasRole('WRITE')")
    override fun findBy(query: Q): Flux<out Key<T>>

    @PreAuthorize("hasRole('WRITE')")
    override fun findUnique(query: Q): Mono<out Key<T>>
}