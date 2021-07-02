package com.demo.chat.service

import com.demo.chat.domain.Key
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class StringRoleAuthorizationMetadata<T>(
    override val uid: T,
    override val target: T,
    override val permission: String
) : AuthMetadata<T, String>

interface AuthenticationService<T, E, V> {
    fun createAuthentication(uid: T, pw: V): Mono<Void>
    // fun disableAuthentication(uid: T): Mono<Void>
    fun authenticate(n: E, pw: V): Mono<out Key<T>>
}

interface AuthorizationService<T, M> {
    fun authorize(authorization: M, exist: Boolean): Mono<Void>
    fun getAuthorizationsFor(uid: T): Flux<M>
    fun getAuthorizationsAgainst(src: T, target: T): Flux<M>
}

interface CoarseAuthorizationService<T>: AuthorizationService<T, String>
interface GranularAuthorizationService<T, P>: AuthorizationService<T, AuthMetadata<T, P>>

interface AuthMetadata<T, P> {
    val uid: T
    val target: T
    val permission: P
}

interface AuthService<T, E, P, V> : AuthenticationService<T, E, V> {
    fun isAuthorizedFor(authorization: AuthMetadata<T, P>): Mono<Boolean>
}