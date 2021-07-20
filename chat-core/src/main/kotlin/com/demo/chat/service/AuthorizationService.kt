package com.demo.chat.service

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyBearer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class StringRoleAuthorizationMetadata<T>(
    override val key: Key<T>,
    override val principal: Key<T>,
    override val target: Key<T>,
    override val permission: String
) : AuthMetadata<T, String>

interface AuthenticationService<T, E, V> {
    fun setAuthentication(uid: Key<T>, pw: V): Mono<Void>
    fun authenticate(n: E, pw: V): Mono<out Key<T>>
}

interface AuthorizationService<T, out M, in N> {
    fun authorize(authorization: N, exist: Boolean): Mono<Void>
    fun getAuthorizationsFor(uid: Key<T>): Flux<out M>
    fun getAuthorizationsAgainst(uidA: Key<T>, uidB: Key<T>): Flux<out M>
}

interface AuthMetadata<T, out P> {
    val key: Key<T>
    val principal: Key<T>
    val target: Key<T>
    val permission: P
}

interface CoarseAuthorizationService<T> : AuthorizationService<T, String, String>
interface GranularAuthorizationService<T, P> :
    AuthorizationService<Key<T>, AuthMetadata<T, P>, AuthMetadata<T, P>>
