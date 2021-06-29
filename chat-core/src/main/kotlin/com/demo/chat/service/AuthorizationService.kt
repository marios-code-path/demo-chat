package com.demo.chat.service

import com.demo.chat.domain.Key
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class AuthorizationMeta<T>(
    override val uid: T,
    override val target: T,
    override val permission: String
) : AuthMetadata<T, String>

interface AuthenticationService<T, E, V> {
    fun createAuthentication(uid: T, pw: V): Mono<Void>
    fun authenticate(n: E, pw: V): Mono<out Key<T>>
}

interface AuthorizationService<T, M> {
    fun authorize(authorization: M, exist: Boolean): Mono<Void>
    fun findAuthorizationsFor(uid: T): Flux<M>
}

interface AppAuthorizationService<T>: AuthorizationService<T, String>

interface AuthMetadata<T, P> {
    val uid: T
    val target: T
    val permission: P
}

// TODO: NO
interface AuthService<M, T, E, V> : AuthenticationService<T, E, V>, AuthorizationService<T, AuthMetadata<T, String>>