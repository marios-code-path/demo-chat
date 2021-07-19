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

interface AuthorizationService<T, M> {
    fun authorize(authorization: M, exist: Boolean): Mono<Void>
    fun getAuthorizationsFor(uid: Key<T>): Flux<M>
    fun getAuthorizationsAgainst(uidA: Key<T>, uidB: Key<T>): Flux<M>
}

interface AuthMetadata<T, P> {
    val key: Key<T>
    val principal: Key<T>
    val target: Key<T>
    val permission: P
}

interface CoarseAuthorizationService<T>: AuthorizationService<T, String>
interface GranularAuthorizationService<T, P>: AuthorizationService<T, AuthMetadata<T, P>>
