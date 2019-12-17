package com.demo.chat.service

import com.demo.chat.domain.Key
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

data class AuthorizationMeta(val uid: UUID, val target: UUID, val permission: String)

interface ChatAuthService<K> {
    fun createAuthentication(uid: K, pw: String): Mono<Void>
    fun authenticate(n: String, pw: String): Mono<out Key<out K>>
    fun authorize(uid: K, target: K, action: String): Mono<Void>
    fun findAuthorizationsFor(uid: K): Flux<AuthorizationMeta>
}