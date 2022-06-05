package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


/**
 * A service that determins when entity given Key<T> has Authorizations [M && N] where
 * T = Key Type
 * M = Authorization Type (out bound)
 * N = Authorization Type (in bound)
 */
interface AuthorizationService<T, out M, in N> {
    fun authorize(authorization: N, exist: Boolean): Mono<Void>
    fun getAuthorizationsForTarget(uid: Key<T>): Flux<out M>
    fun getAuthorizationsForPrincipal(uid: Key<T>): Flux<out M>
    fun getAuthorizationsAgainst(uidA: Key<T>, uidB: Key<T>): Flux<out M>
}