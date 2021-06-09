package com.demo.chat.service

import com.demo.chat.domain.Key
import com.demo.chat.domain.RoleBinding
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class AuthorizationMeta<T>(val uid: T, val target: T, val permission: String)
interface ChatAuthenticationService<T> {
    fun createAuthentication(uid: T, pw: String): Mono<Void>
    fun authenticate(n: String, pw: String): Mono<out Key<T>>
}

interface ChatAuthService<T> : ChatAuthenticationService<T> {
    fun authorize(principal: T, target: T, role: String): Mono<Void>
//    fun deAuthorize(principal: T, target: T, role: String): Mono<Void>
//    fun getRoleBindingsFor(id: T): Flux<RoleBinding<T>>
    fun findAuthorizationsFor(uid: T): Flux<AuthorizationMeta<T>>
}