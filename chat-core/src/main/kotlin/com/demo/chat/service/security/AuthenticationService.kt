package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

interface AuthenticationService<T, E, V> {
    fun setAuthentication(uid: Key<T>, pw: V): Mono<Void>
    fun authenticate(n: E, pw: V): Mono<out Key<T>>
}