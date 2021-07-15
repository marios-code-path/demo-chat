package com.demo.chat.service.impl.memory.auth

import com.demo.chat.domain.Key
import com.demo.chat.domain.UsernamePasswordAuthenticationException
import com.demo.chat.service.AuthenticationService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PasswordStore
import reactor.core.publisher.Mono
import java.util.function.BiFunction
import java.util.function.Function

// E == username type
// T == key type
// U == UserObject type(???)
// V == password type
// Q == searchQuery Type(???)
open class AuthenticationServiceImpl<E, T, U, V, Q>(
    private val userIndex: IndexService<T, U, Q>,
    private val passwordStore: PasswordStore<T, V>,
    private val passwordValidator: BiFunction<V, V, Boolean>,
    private val userHandleToQuery: Function<E, Q>
) : AuthenticationService<T, E, V> {
    override fun createAuthentication(uid: T, pw: V): Mono<Void> = passwordStore.addCredential(Key.funKey(uid), pw)

    override fun authenticate(n: E, pw: V): Mono<out Key<T>> =
        userIndex
            .findUnique(userHandleToQuery.apply(n))
            .flatMap { userKey ->
                passwordStore
                    .getStoredCredentials(userKey)
                    .map { secure ->
                        if (!passwordValidator.apply(pw, secure)) throw UsernamePasswordAuthenticationException
                        userKey
                    }
            }
            .switchIfEmpty(Mono.error(UsernamePasswordAuthenticationException))
}