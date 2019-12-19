package com.demo.chat.service

import com.demo.chat.domain.Key
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

data class AuthorizationMeta<T>(val uid: T, val target: T, val permission: String)

interface ChatAuthService<T> {
    fun createAuthentication(uid: T, pw: String): Mono<Void>
    fun authenticate(n: String, pw: String): Mono<out Key<T>>
    fun authorize(uid: T, target: T, action: String): Mono<Void>
    fun findAuthorizationsFor(uid: T): Flux<AuthorizationMeta<T>>
}