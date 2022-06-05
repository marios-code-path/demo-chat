package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

interface AuthenticationService<T> {
    fun setAuthentication(uid: Key<T>, pw: String): Mono<Void>
    fun authenticate(n: String, pw: String): Mono<out Key<T>>
}