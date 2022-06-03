package com.demo.chat.service.security

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AuthorizationService<T, out M, in N> {
    fun authorize(authorization: N, exist: Boolean): Mono<Void>
    fun getAuthorizationsForTarget(uid: Key<T>): Flux<out M>
    fun getAuthorizationsForPrincipal(uid: Key<T>): Flux<out M>
    fun getAuthorizationsAgainst(uidA: Key<T>, uidB: Key<T>): Flux<out M>
}

interface CoarseAuthorizationService<T> : AuthorizationService<T, String, String>
interface GranularAuthorizationService<T, P> :
    AuthorizationService<Key<T>, AuthMetadata<T, P>, AuthMetadata<T, P>>
