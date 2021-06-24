package com.demo.chat.service

import com.demo.chat.domain.Key
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class AuthorizationMeta<T>(val uid: T, val target: T, val permission: String)

interface AuthenticationService<T> {
    fun createAuthentication(uid: T, pw: String): Mono<Void>
    fun authenticate(n: String, pw: String): Mono<out Key<T>>
}

interface AuthService<T> : AuthenticationService<T> {
    fun authorize(principal: T, target: T, role: String, exist: Boolean): Mono<Void>
    fun findAuthorizationsFor(uid: T): Flux<AuthorizationMeta<T>>
}