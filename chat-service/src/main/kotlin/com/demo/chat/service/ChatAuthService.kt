package com.demo.chat.service

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

data class AuthorizationMeta(val uid: UUID, val target: UUID, val permission: String)

interface ChatAuthService<K> {
    fun createAuthentication(uid: UUID, password: String): Mono<Void>
    fun authenticate(name: String, password: String): Mono<K>
    fun authorize(uid: UUID, target: UUID, action: String): Mono<Void>
    fun findAuthorizationsFor(uid: UUID): Flux<AuthorizationMeta>
}